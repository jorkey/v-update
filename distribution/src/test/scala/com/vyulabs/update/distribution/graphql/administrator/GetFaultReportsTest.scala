package com.vyulabs.update.distribution.graphql.administrator

import java.util.Date
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.{ActorMaterializer, Materializer}
import com.vyulabs.update.common.common.Common._
import com.vyulabs.update.distribution.TestEnvironment
import com.vyulabs.update.distribution.graphql.{GraphqlContext, GraphqlSchema}
import com.vyulabs.update.distribution.mongo.FaultReportDocument
import com.vyulabs.update.common.info.{DistributionFaultReport, FaultInfo, ServiceFaultReport, ServiceState}
import com.vyulabs.update.common.info.{UserInfo, UserRole}
import com.vyulabs.update.distribution.graphql.GraphqlSchema
import sangria.macros.LiteralGraphQLStringContext
import spray.json._

import scala.concurrent.ExecutionContext

class GetFaultReportsTest extends TestEnvironment {
  behavior of "Fault Report Requests"

  implicit val system = ActorSystem("Distribution")
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(null, ex => { ex.printStackTrace(); log.error("Uncatched exception", ex) })

  val collection = result(collections.State_FaultReportsInfo)

  val graphqlContext = new GraphqlContext(UserInfo("admin", UserRole.Administrator), workspace)

  val distribution1 = "distribution1"
  val distribution2 = "distribution2"

  val instance1 = "instance1"
  val instance2 = "instance2"

  override def beforeAll() = {
    result(collection.insert(
      FaultReportDocument(0, DistributionFaultReport(distribution1, ServiceFaultReport("fault1",
        FaultInfo(new Date(), instance1, "directory", "serviceA", CommonServiceProfile, ServiceState(new Date(), None, None, None, None, None, None, None), Seq.empty),
        Seq("fault.info", "core"))))))
    result(collection.insert(
      FaultReportDocument(1, DistributionFaultReport(distribution2, ServiceFaultReport("fault2",
        FaultInfo(new Date(), instance1, "directory", "serviceA", CommonServiceProfile, ServiceState(new Date(), None, None, None, None, None, None, None), Seq.empty),
        Seq("fault.info", "core1"))))))
    result(collection.insert(
      FaultReportDocument(2, DistributionFaultReport(distribution1, ServiceFaultReport("fault3",
        FaultInfo(new Date(), instance2, "directory", "serviceB", CommonServiceProfile, ServiceState(new Date(), None, None, None, None, None, None, None), Seq.empty),
        Seq("fault.info", "core"))))))
  }

  it should "get last fault reports for specified client" in {
    assertResult((OK,
      ("""{"data":{"faultReportsInfo":[{"distributionName":"distribution1","report":{"faultId":"fault3","info":{"serviceName":"serviceB","instanceId":"instance2"},"files":["fault.info","core"]}}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.AdministratorSchemaDefinition, graphqlContext, graphql"""
        query {
          faultReportsInfo (distribution: "distribution1", last: 1) {
            distributionName
            report {
              faultId
              info {
                serviceName
                instanceId
              }
              files
            }
          }
        }
      """))
    )
  }

  it should "get last fault reports for specified service" in {
    assertResult((OK,
      ("""{"data":{"faultReportsInfo":[{"distributionName":"distribution2","report":{"faultId":"fault2","info":{"serviceName":"serviceA","instanceId":"instance1"},"files":["fault.info","core1"]}}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.AdministratorSchemaDefinition, graphqlContext, graphql"""
        query {
          faultReportsInfo (service: "serviceA", last: 1) {
            distributionName
            report {
              faultId
              info {
                serviceName
                instanceId
              }
              files
            }
          }
        }
      """))
    )
  }

  it should "get fault reports for specified service in parameters" in {
    assertResult((OK,
      ("""{"data":{"faultReportsInfo":[{"distributionName":"distribution1","report":{"faultId":"fault3","info":{"serviceName":"serviceB","instanceId":"instance2"},"files":["fault.info","core"]}}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.AdministratorSchemaDefinition,
        graphqlContext, graphql"""
          query FaultsQuery($$service: String!) {
            faultReportsInfo (service: $$service) {
              distributionName
              report {
                faultId
                info {
                  serviceName
                  instanceId
                }
                files
              }
            }
          }
        """, None, variables = JsObject("service" -> JsString("serviceB")))))
  }
}