package caliban

import caliban.Rendering.renderTypes
import caliban.execution.Executor
import caliban.introspection.Introspector
import caliban.introspection.adt.__Introspection
import caliban.parsing.Parser
import caliban.parsing.adt.Value
import caliban.schema.RootSchema.Operation
import caliban.schema._
import caliban.validation.Validator
import zio.IO

class GraphQL[Q, M, S](schema: RootSchema[Q, M, S]) {

  private val rootType =
    RootType(
      schema.query.schema.toType(),
      schema.mutation.map(_.schema.toType()),
      schema.subscription.map(_.schema.toType())
    )
  private val introspectionRootSchema: RootSchema[__Introspection, Nothing, Nothing] = Introspector.introspect(rootType)
  private val introspectionRootType                                                  = RootType(introspectionRootSchema.query.schema.toType(), None, None)

  def render: String = renderTypes(rootType.types)

  def execute(
    query: String,
    operationName: Option[String] = None,
    variables: Map[String, Value] = Map()
  ): IO[CalibanError, ResponseValue] =
    for {
      document        <- Parser.parseQuery(query)
      intro           = Introspector.isIntrospection(document)
      typeToValidate  = if (intro) introspectionRootType else rootType
      _               <- Validator.validate(document, typeToValidate)
      schemaToExecute = if (intro) introspectionRootSchema else schema
      result          <- Executor.executeRequest(document, schemaToExecute, operationName, variables)
    } yield result
}

object GraphQL {

  def graphQL[Q, M, S: SubscriptionSchema](
    resolver: RootResolver[Q, M, S]
  )(implicit querySchema: Schema[Q], mutationSchema: Schema[M], subscriptionSchema: Schema[S]): GraphQL[Q, M, S] =
    new GraphQL[Q, M, S](
      RootSchema(
        Operation(querySchema, resolver.queryResolver),
        resolver.mutationResolver.map(Operation(mutationSchema, _)),
        resolver.subscriptionResolver.map(Operation(subscriptionSchema, _))
      )
    )

}