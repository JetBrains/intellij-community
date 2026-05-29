package com.intellij.mcpserver.elicitation

import com.intellij.mcpserver.elicitation.ElicitationResult.Accept
import com.intellij.mcpserver.elicitation.ElicitationResult.Cancel
import com.intellij.mcpserver.elicitation.ElicitationResult.Decline
import io.modelcontextprotocol.kotlin.sdk.types.BooleanSchema
import io.modelcontextprotocol.kotlin.sdk.types.DoubleSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.IntegerSchema
import io.modelcontextprotocol.kotlin.sdk.types.PrimitiveSchemaDefinition
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.StringSchemaFormat
import io.modelcontextprotocol.kotlin.sdk.types.TitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.TitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import io.modelcontextprotocol.kotlin.sdk.types.EnumOption as SdkEnumOption


internal fun ElicitationForm.toRequestedSchema(): ElicitRequestParams.RequestedSchema {
  val properties: Map<String, PrimitiveSchemaDefinition> =
    fields.associate { field -> field.name to field.toPrimitiveSchema() }
  val required: List<String>? =
    fields.filter { it.required }.map { it.name }.takeIf { it.isNotEmpty() }
  return ElicitRequestParams.RequestedSchema(properties = properties, required = required)
}

internal fun <T> ElicitResult.toElicitationResult(deserializer: DeserializationStrategy<T>): ElicitationResult<T> =
  when (action) {
    ElicitResult.Action.Accept -> {
      val typedContent = mcpElicitationJson.decodeFromJsonElement(deserializer, content ?: JsonObject(emptyMap()))
      Accept(typedContent)
    }
    ElicitResult.Action.Decline -> Decline
    ElicitResult.Action.Cancel -> Cancel
  }

private val mcpElicitationJson: Json = Json {
  ignoreUnknownKeys = true
  isLenient = true
}

private fun ElicitationField.toPrimitiveSchema(): PrimitiveSchemaDefinition =
  when (this) {
    is StringField -> toSchema()
    is IntegerField -> toSchema()
    is NumberField -> toSchema()
    is BooleanField -> toSchema()
    is SingleSelectField -> toSchema()
    is MultiSelectField -> toSchema()
  }

private fun StringField.toSchema(): PrimitiveSchemaDefinition = StringSchema(
  title = title,
  description = description,
  minLength = minLength,
  maxLength = maxLength,
  format = format?.toSdk(),
  default = default,
)

private fun IntegerField.toSchema(): PrimitiveSchemaDefinition = IntegerSchema(
  title = title,
  description = description,
  minimum = minimum,
  maximum = maximum,
  default = default,
)

private fun NumberField.toSchema(): PrimitiveSchemaDefinition = DoubleSchema(
  title = title,
  description = description,
  minimum = minimum,
  maximum = maximum,
  default = default,
)

private fun BooleanField.toSchema(): PrimitiveSchemaDefinition = BooleanSchema(
  title = title,
  description = description,
  default = default,
)

private fun SingleSelectField.toSchema(): PrimitiveSchemaDefinition =
  if (options.allUntitled()) {
    UntitledSingleSelectEnumSchema(
      title = title,
      description = description,
      enumValues = options.map { it.value },
      default = default,
    )
  }
  else {
    TitledSingleSelectEnumSchema(
      title = title,
      description = description,
      oneOf = options.map { SdkEnumOption(const = it.value, title = it.title ?: it.value) },
      default = default,
    )
  }

private fun MultiSelectField.toSchema(): PrimitiveSchemaDefinition =
  if (options.allUntitled()) {
    UntitledMultiSelectEnumSchema(
      title = title,
      description = description,
      minItems = minItems,
      maxItems = maxItems,
      items = UntitledMultiSelectEnumSchema.Items(enumValues = options.map { it.value }),
      default = default,
    )
  }
  else {
    TitledMultiSelectEnumSchema(
      title = title,
      description = description,
      minItems = minItems,
      maxItems = maxItems,
      items = TitledMultiSelectEnumSchema.Items(
        anyOf = options.map { SdkEnumOption(const = it.value, title = it.title ?: it.value) }
      ),
      default = default,
    )
  }

private fun List<EnumOption>.allUntitled(): Boolean = all { it.title == null }

private fun StringFieldFormat.toSdk(): StringSchemaFormat =
  when (this) {
    StringFieldFormat.Email -> StringSchemaFormat.Email
    StringFieldFormat.Uri -> StringSchemaFormat.Uri
    StringFieldFormat.Date -> StringSchemaFormat.Date
    StringFieldFormat.DateTime -> StringSchemaFormat.DateTime
  }