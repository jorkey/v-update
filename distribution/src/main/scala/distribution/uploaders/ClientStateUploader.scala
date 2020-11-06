package distribution.uploaders

import java.io.File
import java.net.URL
import java.util.Date
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FutureDirectives
import akka.stream.Materializer
import com.vyulabs.update.common.Common
import com.vyulabs.update.common.Common.{InstanceId, ServiceDirectory, ServiceName}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vyulabs.update.distribution.{DistributionDirectory, DistributionDirectoryClient, DistributionMain}
import com.vyulabs.update.info.{InstanceServiceState, ProfiledServiceName, ServiceState}

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 22.05.19.
  * Copyright FanDate, Inc.
  */
class ClientStateUploader(dir: DistributionDirectory, developerDirectoryUrl: URL, instanceId: InstanceId, installerDir: String)
                         (implicit system: ActorSystem, materializer: Materializer)
      extends Thread with FutureDirectives with SprayJsonSupport { self =>
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  private val developerDirectory = new DistributionDirectoryClient(developerDirectoryUrl)

  private var instancesStates = Map.empty[InstanceId, Map[ServiceDirectory, Map[ServiceName, ServiceState]]]
  private var statesToUpload = Map.empty[InstanceId, Map[ServiceDirectory, Map[ServiceName, ServiceState]]]

  private val expirationPeriod = FiniteDuration.apply(1, TimeUnit.MINUTES).toMillis
  private val uploadInterval = 10000

  private var lastStatesUploadTime = 0L
  private var desiredVersionsLmt = 0L

  private var stopping = false

  def receiveState(instanceId: InstanceId, servicesState: Seq[InstanceServiceState]): Route = {
    /* TODO graphql
    self.synchronized {
      instancesStates += (instanceId -> (instancesStates.getOrElse(instanceId, Map.empty) ++ servicesState.directories))
      statesToUpload += (instanceId -> (statesToUpload.getOrElse(instanceId, Map.empty) ++ servicesState.directories))
    }*/
    complete(StatusCodes.OK)
  }

  def getInstanceState(instanceId: InstanceId): Route = {
    /* TODO graphql
    self.synchronized {
      instancesStates.get(instanceId) match {
        case Some(state) =>
          complete(ServicesState(state))
        case None =>
          complete(StatusCodes.NotFound)
      }
    }*/
    complete(StatusCodes.NotFound)
  }

  def close(): Unit = {
    self.synchronized {
      stopping = true
      notify()
    }
    join()
  }

  override def run(): Unit = {
    /* TODO graphql
    log.info("State uploader started")
    try {
      while (true) {
        val pause = lastStatesUploadTime + uploadInterval - System.currentTimeMillis()
        if (pause > 0) {
          self.synchronized {
            if (stopping) {
              return
            }
            wait(pause)
            if (stopping) {
              return
            }
          }
        }
        if (!stopping) {
          try {
            removeOldStates()
            mayBeUploadInstalledDesiredVersions()

            val states = self.synchronized {
              val states = statesToUpload
              statesToUpload = Map.empty
              InstancesStateMap(states.mapValues(ServicesState(_)))
            }.merge(InstancesStateMap(Map.empty + (instanceId ->
              ServicesState.getOwnInstanceState(Common.DistributionServiceName, new Date(DistributionMain.executionStart))
                .merge(ServicesState.getServiceInstanceState(Common.ScriptsServiceName, new File(".")))
                .merge(ServicesState.getServiceInstanceState(Common.InstallerServiceName, new File(installerDir)))
                .merge(ServicesState.getServiceInstanceState(Common.ScriptsServiceName, new File(installerDir))))))
            log.debug("Upload instances state to developer distribution server")
            if (!developerDirectory.uploadServicesState(states)) {
              log.error("Can't upload instances state")
            }
          } catch {
            case ex: Exception =>
              log.error("State uploader exception", ex)
          }
          lastStatesUploadTime = System.currentTimeMillis()
        }
      }
    } catch {
      case ex: Exception =>
        log.error(s"State uploader thread is failed", ex)
    }
     */
  }

  private def removeOldStates(): Unit = {
    self.synchronized {
      instancesStates = instancesStates.map { case (instanceId, state) =>
        (instanceId, state.map { case (key, state) =>
          (key, state.filter { case (_, state) =>
            System.currentTimeMillis() - state.date.getTime < expirationPeriod
          })})
      }.filter { case (_, state) =>
        !state.isEmpty
      }
    }
  }

  private def mayBeUploadInstalledDesiredVersions(): Unit = {
    /* TODO graphql
    val file = dir.getDesiredVersionsFile()
    val lmt = file.lastModified()
    if (lmt != desiredVersionsLmt) {
      if (developerDirectory.uploadInstalledDesiredVersions(file)) {
        desiredVersionsLmt = lmt
      }
    }*/
  }
}