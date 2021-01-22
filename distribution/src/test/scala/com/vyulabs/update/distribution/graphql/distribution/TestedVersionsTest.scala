package com.vyulabs.update.distribution.graphql.distribution

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.{ActorMaterializer, Materializer}
import com.vyulabs.update.common.config.{DistributionClientConfig, DistributionClientInfo, DistributionClientProfile}
import com.vyulabs.update.common.info._
import com.vyulabs.update.common.version.{DeveloperDistributionVersion, DeveloperVersion}
import com.vyulabs.update.distribution.TestEnvironment
import com.vyulabs.update.distribution.graphql.{GraphqlContext, GraphqlSchema}
import sangria.macros.LiteralGraphQLStringContext
import spray.json._

import java.util.Date
import scala.concurrent.ExecutionContext

class TestedVersionsTest extends TestEnvironment {
  behavior of "Tested Versions Info Requests"

  implicit val system = ActorSystem("Distribution")
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(null, ex => { ex.printStackTrace(); log.error("Uncatched exception", ex) })

  override def beforeAll() = {
    val installProfileCollection = collections.Developer_DistributionClientsProfiles
    val clientInfoCollection = collections.Developer_DistributionClientsInfo

    result(installProfileCollection.insert(DistributionClientProfile("common", Set("service1", "service2"))))

    result(clientInfoCollection.insert(DistributionClientInfo("distribution1", DistributionClientConfig("common", None))))
    result(clientInfoCollection.insert(DistributionClientInfo("distribution2", DistributionClientConfig("common", Some("distribution1")))))
  }

  it should "set/get tested versions" in {
    val graphqlContext1 = new GraphqlContext(UserInfo("distribution1", UserRole.Distribution), workspace)

    assertResult((OK,
      ("""{"data":{"setTestedVersions":true}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.DistributionSchemaDefinition, graphqlContext1, graphql"""
        mutation {
          setTestedVersions (
            versions: [
               { serviceName: "service1", version: "test-1.1.1" },
               { serviceName: "service2", version: "test-2.1.1" }
            ]
          )
        }
      """)))

    val graphqlContext2 = new GraphqlContext(UserInfo("distribution2", UserRole.Distribution), workspace)

    assertResult((OK,
      ("""{"data":{"desiredVersions":[{"serviceName":"service1","version":"test-1.1.1"},{"serviceName":"service2","version":"test-2.1.1"}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.DistributionSchemaDefinition, graphqlContext2, graphql"""
        query {
          desiredVersions {
            serviceName
            version
          }
        }
      """)))

    result(collections.State_TestedVersions.drop())
  }

  it should "return error if no tested versions for the client's profile" in {
    val graphqlContext = new GraphqlContext(UserInfo("distribution2", UserRole.Distribution), workspace)
    assertResult((OK,
      ("""{"data":null,"errors":[{"message":"Desired versions for profile common are not tested by anyone","path":["desiredVersions"],"locations":[{"column":11,"line":3}]}]}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.DistributionSchemaDefinition, graphqlContext, graphql"""
        query {
          desiredVersions {
            serviceName
            version
          }
        }
      """)))
  }

  it should "return error if client required preliminary testing has personal desired versions" in {
    result(collections.State_TestedVersions.insert(
      TestedDesiredVersions("common", Seq(
        DeveloperDesiredVersion("service1", DeveloperDistributionVersion("test", DeveloperVersion(Seq(1, 1, 0))))),
        Seq(TestSignature("test-client", new Date())))))
    result(collections.Client_DesiredVersions.drop())
  }
}
