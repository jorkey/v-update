package com.vyulabs.update.distribution.graphql.administrator

import java.nio.file.Files
import java.util.Date
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.ActorMaterializer
import com.vyulabs.update.config.{ClientConfig, ClientInfo}
import com.vyulabs.update.distribution.DistributionDirectory
import com.vyulabs.update.info.{InstalledDesiredVersions, ClientServiceState, DesiredVersion, ServiceState}
import com.vyulabs.update.lock.SmartFilesLocker
import com.vyulabs.update.users.{UserInfo, UserRole}
import com.vyulabs.update.version.BuildVersion
import distribution.DatabaseCollections
import distribution.config.VersionHistoryConfig
import distribution.graphql.{Graphql, GraphqlContext, GraphqlSchema}
import distribution.mongo.MongoDb
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.slf4j.LoggerFactory
import sangria.macros.LiteralGraphQLStringContext
import spray.json._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Awaitable, ExecutionContext}

class StateInfoTest extends FlatSpec with Matchers with BeforeAndAfterAll {
  behavior of "State Info Requests"

  implicit val system = ActorSystem("Distribution")
  implicit val materializer = ActorMaterializer()

  implicit val log = LoggerFactory.getLogger(this.getClass)

  implicit val executionContext = ExecutionContext.fromExecutor(null, ex => log.error("Uncatched exception", ex))
  implicit val filesLocker = new SmartFilesLocker()

  val versionHistoryConfig = VersionHistoryConfig(5)

  val dir = new DistributionDirectory(Files.createTempDirectory("test").toFile)
  val mongo = new MongoDb(getClass.getSimpleName); result(mongo.dropDatabase())
  val collections = new DatabaseCollections(mongo, "self-instance", Some("builder"), 10)
  val graphql = new Graphql()

  def result[T](awaitable: Awaitable[T]) = Await.result(awaitable, FiniteDuration(3, TimeUnit.SECONDS))
  
  override def beforeAll() = {
    val clientInfoCollection = result(collections.Developer_ClientsInfo)
    val installedVersionsCollection = result(collections.State_InstalledDesiredVersions)
    val clientServiceStatesCollection = result(collections.State_ServiceStates)

    result(clientInfoCollection.insert(
      ClientInfo("client1", ClientConfig("common", Some("test")))))

    result(installedVersionsCollection.insert(
      InstalledDesiredVersions("client1", Seq(DesiredVersion("service1", BuildVersion(1, 1, 1)), DesiredVersion("service2", BuildVersion(2, 1, 3))))))

    result(clientServiceStatesCollection.insert(
      ClientServiceState(Some("client1"), "instance1", "service1", "directory1",
        ServiceState(date = new Date(), None, None, version = Some(BuildVersion(1, 1, 0)), None, None, None, None))))
  }

  override protected def afterAll(): Unit = {
    dir.drop()
    //result(mongo.dropDatabase())
  }

  it should "return installed versions" in {
    val graphqlContext = new GraphqlContext(versionHistoryConfig, dir, collections, UserInfo("user1", UserRole.Client))
    assertResult((OK,
      ("""{"data":{"installedVersions":[{"serviceName":"service1","buildVersion":"1.1.1"},{"serviceName":"service2","buildVersion":"2.1.3"}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.AdministratorSchemaDefinition, graphqlContext, graphql"""
        query {
          installedVersions (client: "client1") {
             serviceName
             buildVersion
          }
        }
      """)))
  }

  it should "return service state" in {
    val graphqlContext = new GraphqlContext(versionHistoryConfig, dir, collections, UserInfo("user1", UserRole.Client))
    assertResult((OK,
      ("""{"data":{"servicesState":[{"instanceId":"instance1","state":{"version":"1.1.0"}}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.AdministratorSchemaDefinition, graphqlContext, graphql"""
        query {
          servicesState (client: "client1", service: "service1") {
            instanceId
            state {
              version
            }
          }
        }
      """))
    )
  }
}
