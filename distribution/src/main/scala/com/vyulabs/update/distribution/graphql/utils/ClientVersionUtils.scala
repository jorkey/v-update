package com.vyulabs.update.distribution.graphql.utils

import com.mongodb.client.model.Filters
import com.vyulabs.update.common.common.Common.{DistributionName, ServiceName}
import com.vyulabs.update.common.distribution.server.DistributionDirectory
import com.vyulabs.update.common.info.{ClientDesiredVersion, ClientDesiredVersions, ClientVersionInfo}
import com.vyulabs.update.common.version.{ClientDistributionVersion, ClientVersion}
import com.vyulabs.update.distribution.config.VersionHistoryConfig
import com.vyulabs.update.distribution.mongo.DatabaseCollections
import org.bson.BsonDocument
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.asJavaIterableConverter
import scala.concurrent.{ExecutionContext, Future}

trait ClientVersionUtils extends DistributionClientsUtils {
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  protected val versionHistoryConfig: VersionHistoryConfig
  protected val dir: DistributionDirectory
  protected val collections: DatabaseCollections

  protected implicit val executionContext: ExecutionContext

  def addClientVersionInfo(versionInfo: ClientVersionInfo): Future[Boolean] = {
    log.info(s"Add client version info ${versionInfo}")
    for {
      result <- collections.Client_VersionsInfo.insert(versionInfo).map(_ => true)
      _ <- removeObsoleteVersions(versionInfo.version.distributionName, versionInfo.serviceName)
    } yield result
  }

  def getClientVersionsInfo(serviceName: ServiceName, distributionName: Option[DistributionName] = None,
                            version: Option[ClientDistributionVersion] = None): Future[Seq[ClientVersionInfo]] = {
    val serviceArg = Filters.eq("serviceName", serviceName)
    val distributionArg = distributionName.map { distributionName => Filters.eq("version.distributionName", distributionName ) }
    val versionArg = version.map { version => Filters.eq("version", version) }
    val filters = Filters.and((Seq(serviceArg) ++ distributionArg ++ versionArg).asJava)
    collections.Client_VersionsInfo.find(filters)
  }

  private def removeObsoleteVersions(distributionName: DistributionName, serviceName: ServiceName): Future[Unit] = {
    for {
      versions <- getClientVersionsInfo(serviceName, distributionName = Some(distributionName))
      busyVersions <- getBusyVersions(distributionName, serviceName)
      _ <- {
        val notUsedVersions = versions.filterNot(info => busyVersions.contains(info.version.version))
          .sortBy(_.buildInfo.date.getTime).map(_.version)
        if (notUsedVersions.size > versionHistoryConfig.maxSize) {
          Future.sequence(notUsedVersions.take(notUsedVersions.size - versionHistoryConfig.maxSize).map { version =>
            removeClientVersion(serviceName, version)
          })
        } else {
          Future()
        }
      }
    } yield {}
  }

  def removeClientVersion(serviceName: ServiceName, version: ClientDistributionVersion): Future[Boolean] = {
    log.info(s"Remove client version ${version} of service ${serviceName}")
    val filters = Filters.and(
      Filters.eq("serviceName", serviceName),
      Filters.eq("version", version))
    dir.getClientVersionImageFile(serviceName, version).delete()
    collections.Client_VersionsInfo.delete(filters).map(_ > 0)
  }

  def getClientDesiredVersions(serviceNames: Set[ServiceName] = Set.empty): Future[Seq[ClientDesiredVersion]] = {
    collections.Client_DesiredVersions.find(new BsonDocument()).map(_.map(_.versions).headOption.getOrElse(Seq.empty[ClientDesiredVersion])
      .filter(v => serviceNames.isEmpty || serviceNames.contains(v.serviceName)).sortBy(_.serviceName))
  }

  def setClientDesiredVersions(desiredVersions: Seq[ClientDesiredVersion]): Future[Boolean] = {
    collections.Client_DesiredVersions.update(new BsonDocument(), _ => Some(ClientDesiredVersions(desiredVersions))).map(_ => true)
  }

  def getClientDesiredVersion(serviceName: ServiceName): Future[Option[ClientDistributionVersion]] = {
    getClientDesiredVersions(Set(serviceName)).map(_.headOption.map(_.version))
  }

  def getDistributionClientDesiredVersions(distributionName: DistributionName): Future[Seq[ClientDesiredVersion]] = {
    val filters = Filters.eq("distributionName", distributionName)
    collections.Client_DesiredVersions.find(filters).map(_.map(_.versions).headOption.getOrElse(Seq.empty[ClientDesiredVersion]))
  }

  private def getBusyVersions(distributionName: DistributionName, serviceName: ServiceName): Future[Set[ClientVersion]] = {
    getClientDesiredVersion(serviceName).map(_.toSet.filter(_.distributionName == distributionName).map(_.version))
  }
}
