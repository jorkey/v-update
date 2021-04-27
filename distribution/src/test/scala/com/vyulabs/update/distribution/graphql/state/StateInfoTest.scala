package com.vyulabs.update.distribution.graphql.state

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.{ActorMaterializer, Materializer}
import com.vyulabs.update.common.info._
import com.vyulabs.update.common.utils.JsonFormats._
import com.vyulabs.update.common.version.{ClientDistributionVersion, DeveloperDistributionVersion}
import com.vyulabs.update.distribution.TestEnvironment
import com.vyulabs.update.distribution.graphql.{GraphqlContext, GraphqlSchema}
import com.vyulabs.update.distribution.mongo.InstalledDesiredVersions
import sangria.macros.LiteralGraphQLStringContext
import spray.json._

import java.util.Date
import scala.concurrent.ExecutionContext

class StateInfoTest extends TestEnvironment {
  behavior of "Tested Versions Info Requests"

  implicit val system = ActorSystem("Distribution")
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(null, ex => { ex.printStackTrace(); log.error("Uncatched exception", ex) })

  override protected def beforeAll(): Unit = {
    result(collections.Developer_ConsumersInfo.insert(DistributionConsumerInfo("distribution", "common", Some("test"))))
  }

  it should "set tested versions" in {
    assertResult((OK,
      ("""{"data":{"setTestedVersions":true}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.SchemaDefinition, distributionContext, graphql"""
        mutation {
          setTestedVersions (
            versions: [
              { service: "service1", version: { distribution: "test", build: [1,1,2] } },
              { service: "service2", version: { distribution: "test", build: [2,1,2] } }
            ]
          )
        }
      """)))

    val date = new Date()

    assertResult(Seq(TestedDesiredVersions("common", Seq(
      DeveloperDesiredVersion("service1", DeveloperDistributionVersion("test", Seq(1, 1, 2))),
      DeveloperDesiredVersion("service2", DeveloperDistributionVersion("test", Seq(2, 1, 2)))),
      Seq(TestSignature("distribution", date)))))(result(collections.State_TestedVersions.find().map(_.map(v => TestedDesiredVersions(
        v.consumerProfile, v.versions, v.signatures.map(s => TestSignature(s.distribution, date)))))))
    result(collections.State_TestedVersions.drop())
  }

  it should "set/get installed desired versions" in {
    assertResult((OK,
      ("""{"data":{"setInstalledDesiredVersions":true}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.SchemaDefinition, distributionContext, graphql"""
        mutation {
          setInstalledDesiredVersions (
            versions: [
               { service: "service1", version: { distribution: "test", developerBuild: [1,1,1], clientBuild: 0 } },
               { service: "service2", version: { distribution: "test", developerBuild: [2,1,1], clientBuild: 0 } }
            ]
          )
        }
      """)))

    assertResult((OK,
      ("""{"data":{"installedDesiredVersions":[{"service":"service1","version":{"distribution":"test","developerBuild":[1,1,1],"clientBuild":0}},{"service":"service2","version":{"distribution":"test","developerBuild":[2,1,1],"clientBuild":0}}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.SchemaDefinition, adminContext, graphql"""
        query {
          installedDesiredVersions (distribution: "distribution") {
            service
            version { distribution, developerBuild, clientBuild }
          }
        }
      """)))

    result(collections.State_InstalledDesiredVersions.find().map(assertResult(Seq(InstalledDesiredVersions("distribution", Seq(
      ClientDesiredVersion("service1", ClientDistributionVersion("test", Seq(1, 1, 1), 0)),
      ClientDesiredVersion("service2", ClientDistributionVersion("test", Seq(2, 1, 1), 0))))))(_)))
    result(collections.State_InstalledDesiredVersions.drop())
  }

  it should "set services state" in {
    val distributionContext = GraphqlContext(Some(AccessToken("distribution", Seq(UserRole.Distribution))), workspace)

    assertResult((OK,
      ("""{"data":{"setServiceStates":true}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.SchemaDefinition, distributionContext, graphql"""
        mutation ServicesState($$date: Date!) {
          setServiceStates (
            states: [
              { instance: "instance1", service: "service1", directory: "dir",
                  state: { date: $$date, version: { distribution: "test", developerBuild: [1,2,3], clientBuild: 0 } }
              }
            ]
          )
        }
      """, variables = JsObject("date" -> new Date().toJson))))

    assertResult((OK,
      ("""{"data":{"setServiceStates":true}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.SchemaDefinition, distributionContext, graphql"""
        mutation ServicesState($$date: Date!) {
          setServiceStates (
            states: [
              { instance: "instance1", service: "service1", directory: "dir",
                  state: { date: $$date, version: { distribution: "test", developerBuild: [1,2,4], clientBuild: 0 } }
              }
            ]
          )
        }
      """, variables = JsObject("date" -> new Date().toJson))))

    assertResult((OK,
      ("""{"data":{"serviceStates":[{"instance":{"instance":"instance1","state":{"version":{"distribution":"test","developerBuild":[1,2,4],"clientBuild":0}}}}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.SchemaDefinition, adminContext, graphql"""
        query {
          serviceStates (distribution: "distribution", service: "service1") {
            instance {
              instance
              state {
                version { distribution, developerBuild, clientBuild }
              }
            }
          }
        }
      """))
    )

    result(collections.State_ServiceStates.drop())
  }
}