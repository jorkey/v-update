package com.vyulabs.update.distribution.developer

import java.net.URL

import com.vyulabs.update.common.Common.{ClientName, ServiceName}
import com.vyulabs.update.distribution.DistributionDirectoryClient
import com.vyulabs.update.info.{DesiredVersion, VersionsInfo}
import org.slf4j.Logger
import com.vyulabs.update.info.VersionsInfoJson._
import spray.json.enrichAny

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 23.04.19.
  * Copyright FanDate, Inc.
  */
class DeveloperDistributionDirectoryAdmin(val url: URL)(implicit log: Logger) extends DistributionDirectoryClient(url)
    with DeveloperDistributionWebPaths {

  def downloadVersionsInfo(clientName: Option[ClientName], serviceName: ServiceName): Option[VersionsInfo] = {
    val url = makeUrl(getDownloadVersionsInfoPath(serviceName, clientName))
    downloadToJson(url) match {
      case Some(json) =>
        Some(json.convertTo[VersionsInfo])
      case None =>
        None
    }
  }

  def downloadDesiredVersions(clientName: Option[ClientName]): Option[Seq[DesiredVersion]] = {
    clientName match {
      case Some(clientName) =>
        log.info(s"Download desired versions for client ${clientName}")
      case None =>
        log.info(s"Download desired versions")
    }
    val url = makeUrl(getDownloadDesiredVersionsPath(clientName))
    downloadToJson(url) match {
      case Some(json) =>
        Some(json.convertTo[Seq[DesiredVersion]])
      case None =>
        None
    }
  }

  def uploadDesiredVersions(clientName: Option[ClientName], desiredVersions: Seq[DesiredVersion]): Boolean = {
    clientName match {
      case Some(clientName) =>
        log.info(s"Upload desired versions for client ${clientName}")
      case None =>
        log.info(s"Upload desired versions")
    }
    uploadFromJson(makeUrl(getUploadDesiredVersionsPath(clientName)),
      desiredVersionsName, uploadDesiredVersionsPath, desiredVersions.toJson)
  }
}
