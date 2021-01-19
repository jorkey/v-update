package com.vyulabs.update.distribution.loaders

import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.directives.FutureDirectives
import akka.stream.Materializer
import com.mongodb.client.model.{Filters, Sorts, Updates}
import com.vyulabs.update.common.common.Common.DistributionName
import com.vyulabs.update.common.distribution.client.DistributionClient
import com.vyulabs.update.common.distribution.client.graphql.{GraphqlArgument, GraphqlMutation}
import com.vyulabs.update.common.distribution.server.DistributionDirectory
import com.vyulabs.update.distribution.client.AkkaHttpClient
import com.vyulabs.update.distribution.client.AkkaHttpClient.AkkaSource
import com.vyulabs.update.distribution.mongo.DatabaseCollections
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.net.URL
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 11.11.20.
  * Copyright FanDate, Inc.
  */

class StateUploader(distributionName: DistributionName,
                    collections: DatabaseCollections, distributionDirectory: DistributionDirectory, uploadIntervalSec: Int,
                    client: DistributionClient[AkkaSource])
                   (implicit system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext)  extends FutureDirectives with SprayJsonSupport { self =>
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  var task = Option.empty[Cancellable]

  def start(): Unit = {
    task = Some(system.scheduler.scheduleOnce(FiniteDuration(uploadIntervalSec, TimeUnit.SECONDS))(uploadState()))
    log.debug("Upload task is scheduled")
  }

  def stop(): Unit = {
    for (task <- task) {
      if (task.cancel() || !task.isCancelled) {
        log.debug("Upload task is cancelled")
      } else {
        log.debug("Upload task failed to cancel")
      }
      this.task = None
    }
  }

  private def uploadState(): Unit = {
    log.debug("Upload state")
    val result = for {
      _ <- uploadServiceStates().andThen {
        case Failure(ex) =>
          log.error("Upload service states error", ex)
      }
      _ <- uploadFaultReports().andThen {
        case Failure(ex) =>
          log.error("Upload fault reports error", ex)
      }
    } yield {}
    result.andThen {
      case result =>
        if (result.isSuccess) {
          log.debug(s"State is uploaded successfully")
        } else {
          log.debug(s"State is failed to upload")
        }
        system.getScheduler.scheduleOnce(FiniteDuration(uploadIntervalSec, TimeUnit.SECONDS))(uploadState())
        log.debug("Upload task is scheduled")
    }
  }

  private def uploadServiceStates(): Future[Unit] = {
    log.debug("Upload service states")
    for {
      serviceStates <- collections.State_ServiceStates
      fromSequence <- getLastUploadSequence(serviceStates.getName())
      newStatesDocuments <- serviceStates.find(Filters.gt("sequence", fromSequence), sort = Some(Sorts.ascending("sequence")))
      newStates <- Future(newStatesDocuments.map(_.content))
    } yield {
      if (!newStates.isEmpty) {
        client.graphqlRequest(GraphqlMutation[Boolean]("setServiceStates", Seq(GraphqlArgument("state" -> newStates.toJson)))).
          andThen {
            case Success(_) =>
              setLastUploadSequence(serviceStates.getName(), newStatesDocuments.last.sequence)
            case Failure(ex) =>
              setLastUploadError(serviceStates.getName(), ex.getMessage)
          }
      } else {
        Promise[Unit].success(Unit).future
      }
    }
  }

  private def uploadFaultReports(): Future[Unit] = {
    log.debug("Upload fault reports")
    for {
      faultReports <- collections.State_FaultReportsInfo
      fromSequence <- getLastUploadSequence(faultReports.getName())
      newReportsDocuments <- faultReports.find(Filters.gt("_id", fromSequence), sort = Some(Sorts.ascending("_id")))
      newReports <- Future(newReportsDocuments.map(_.content))
    } yield {
      if (!newReports.isEmpty) {
        Future.sequence(newReports.filter(_.distributionName == distributionName).map(report => {
          val file = distributionDirectory.getFaultReportFile(report.report.faultId)
          val infoUpload = for {
            _ <- client.uploadFaultReport(report.report.faultId, file)
            _ <- client.graphqlRequest(GraphqlMutation[Boolean]("addServiceFaultReportInfo", Seq(GraphqlArgument("fault" -> report.report.toJson))))
          } yield {}
          infoUpload.
            andThen {
              case Success(_) =>
                setLastUploadSequence(faultReports.getName(), newReportsDocuments.last._id)
              case Failure(ex) =>
                setLastUploadError(faultReports.getName(), ex.getMessage)
            }
        }))
      } else {
        Promise[Unit].success(Unit).future
      }
    }
  }

  private def getLastUploadSequence(component: String): Future[Long] = {
    for {
      uploadStatus <- collections.State_UploadStatus
      sequence <- uploadStatus.find(Filters.eq("component", component)).map(_.headOption.map(_.status.lastUploadSequence).flatten.getOrElse(-1L))
    } yield sequence
  }

  private def setLastUploadSequence(component: String, sequence: Long): Future[Boolean] = {
    for {
      uploadStatus <- collections.State_UploadStatus
      result <- uploadStatus.updateOne(Filters.eq("component", component),
        Updates.combine(Updates.set("status.lastUploadSequence", sequence), Updates.unset("status.lastError")))
        .map(r => r.getModifiedCount > 0)
    } yield result
  }

  private def setLastUploadError(component: String, error: String): Future[Unit] = {
    for {
      uploadStatus <- collections.State_UploadStatus
      _ <- uploadStatus.updateOne(Filters.eq("component", component),
        Updates.set("status.lastError", error))
        .map(r => r.getModifiedCount > 0)
    } yield {}
  }
}

object StateUploader {
  def apply(distributionName: DistributionName,
            collections: DatabaseCollections, distributionDirectory: DistributionDirectory, uploadIntervalSec: Int, distributionUrl: URL)
           (implicit system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext): StateUploader = {
    new StateUploader(distributionName, collections, distributionDirectory, uploadIntervalSec, new DistributionClient(
      distributionName, new AkkaHttpClient(distributionUrl)))
  }
}