package com.vyulabs.update.distribution.graphql.service

import java.util.Date

import akka.http.scaladsl.model.StatusCodes.OK
import com.vyulabs.update.config.{ClientConfig, ClientInfo}
import com.vyulabs.update.distribution.GraphqlTestEnvironment
import com.vyulabs.update.info.{ClientServiceState, DesiredVersion, InstalledDesiredVersions, ServiceState}
import com.vyulabs.update.users.{UserInfo, UserRole}
import com.vyulabs.update.version.BuildVersion
import distribution.graphql.{GraphqlContext, GraphqlSchema}
import sangria.macros.LiteralGraphQLStringContext
import spray.json._
import com.vyulabs.update.utils.Utils.DateJson._

class StateInfoTest extends GraphqlTestEnvironment {
  behavior of "State Info Requests"

  val graphqlContext = new GraphqlContext(versionHistoryConfig, distributionDir, collections, UserInfo("user1", UserRole.Client))

  it should "set/get own service state" in {
    assertResult((OK,
      ("""{"data":{"setServicesState":true}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.ServiceSchemaDefinition, graphqlContext, graphql"""
        mutation ServicesState($$date: Date!) {
          setServicesState (
            state: [
              { instanceId: "instance1", serviceName: "service1", directory: "dir",
                  state: { date: $$date, version: "1.2.3" } }
            ]
          )
        }
      """, variables = JsObject("date" -> new Date().toJson))))

    assertResult((OK,
      ("""{"data":{"serviceState":[{"state":{"version":"1.2.3"}}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.ServiceSchemaDefinition, graphqlContext, graphql"""
        query {
          serviceState (instance: "instance1", service: "service1", directory: "dir") {
            state  {
              version
            }
          }
        }
      """, None, variables = JsObject("directory" -> JsString(ownServicesDir.getCanonicalPath)))))
  }
}