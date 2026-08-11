package com.intellij.mcpserver.impl.util

import com.intellij.mcpserver.McpProjectPathCustomizer
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCallResultContent
import com.intellij.mcpserver.McpToolSchema
import com.intellij.mcpserver.annotations.McpDescription
import io.github.smiley4.schemakenerator.core.CoreSteps.initial
import io.github.smiley4.schemakenerator.core.data.AnnotationData
import io.github.smiley4.schemakenerator.core.data.TypeData
import io.github.smiley4.schemakenerator.core.data.TypeId
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaAnnotationUtils.iterateProperties
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.compileInlining
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.generateJsonSchema
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.handleCoreAnnotations
import io.github.smiley4.schemakenerator.jsonschema.data.IntermediateJsonSchemaData
import io.github.smiley4.schemakenerator.jsonschema.data.JsonSchemaData
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonArray
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonBooleanValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNode
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNullValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNumericValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonObject
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonTextValue
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.analyzeTypeUsingKotlinxSerialization
import io.github.smiley4.schemakenerator.serialization.analyzer.AnnotationAnalyzer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray as KtJsonArray
import kotlinx.serialization.json.JsonObject as KtJsonObject
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

internal fun parametersSchema(callable: KCallable<*>, vararg additionalImplicitParameters: KParameter): McpToolSchema {
  val parameterSchemas = mutableMapOf<String, JsonElement>()
  val definitions = mutableMapOf<String, JsonElement>()
  val requiredParameters = mutableSetOf<String>()

  // probably passthrough something like `additionalImplicitParameters` from outsise
  // but it isn't neccessary right now
  for (parameter in callable.parameters + additionalImplicitParameters) {
    if (parameter.kind != KParameter.Kind.VALUE) continue

    val parameterName = parameter.name ?: error("Parameter has no name: ${parameter.name} in $callable")

    val parameterType = parameter.type

    // JsonElement / JsonElement? can hold any JSON value (object, array, string, etc.).
    // The schemakenerator maps it to type:object, which causes execute_tool to reject arrays.
    // Use an open schema so the execute_tool parser treats the value as a raw string and
    // passes it through; the business logic does its own shape checking via parseStructuredJsonArg.
    if (parameterType == typeOf<JsonElement?>() || parameterType == typeOf<JsonElement>()) {
      val description = parameter.annotations.filterIsInstance<McpDescription>().firstOrNull()?.description
      parameterSchemas[parameterName] = buildJsonObject {
        if (description != null) put("description", description)
      }
      if (!parameter.isOptional) requiredParameters.add(parameterName)
      continue
    }

    val intermediateJsonSchemaData = initial(parameterType)
      .analyzeTypeUsingKotlinxSerialization()
      .generateJsonSchema()
      .handleCoreAnnotations()
      .handleMcpDescriptionAnnotations(parameter)
      .removeNumericBounds()
      .addStringTypeToEnums()

    val schema = intermediateJsonSchemaData.compileInlining()

    parameterSchemas[parameterName] = schema.json.toKt()
    for ((key, def) in schema.definitions) {
      definitions[key] = def.toKt() // overwrite definitions because types can be the same across different parameters
    }
    if (!parameter.isOptional) requiredParameters.add(parameterName)
  }

  // Customize projectPath name and description via EP if needed
  if (additionalImplicitParameters.any { it.name == "projectPath" }) {
    val customizer = McpProjectPathCustomizer.EP.extensionList.firstOrNull()
    if (customizer != null) {
      parameterSchemas.remove("projectPath")?.let { originalSchema ->
        parameterSchemas[customizer.parameterName] = buildJsonObject {
          for ((key, value) in originalSchema as KtJsonObject) {
            if (key == "description") put("description", customizer.parameterDescription) else put(key, value)
          }
        }
      }
    }
  }

  return McpToolSchema.ofPropertiesMap(properties = parameterSchemas, requiredProperties = requiredParameters, definitions = definitions, definitionsPath = McpToolSchema.DEFAULT_DEFINITIONS_PATH)
}

private fun projectPathParameterStub(
  @McpDescription("""
    | The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls. 
    | In the case you know only the current working directory you can use it as the project path.
    | If you're not aware about the project path you can ask user about it.""")
  projectPath: String? = null) {}
internal val projectPathParameter: KParameter get() = ::projectPathParameterStub.parameters.single()
val projectPathParameterName: String get() = McpProjectPathCustomizer.EP.extensionList.firstOrNull()?.parameterName ?: "projectPath"

internal fun returnTypeSchema(callable: KCallable<*>): McpToolSchema? {
  val type = callable.returnType
  // output schema should be provided only for non-primitive types and serializable types
  if (type == typeOf<String>()) return null
  if (type == typeOf<Char>()) return null
  if (type == typeOf<Boolean>()) return null
  if (type == typeOf<Int>()) return null
  if (type == typeOf<Long>()) return null
  if (type == typeOf<Double>()) return null
  if (type == typeOf<Float>()) return null
  if (type == typeOf<Byte>()) return null
  if (type == typeOf<Short>()) return null
  if (type == typeOf<Unit>()) return null
  if (type == typeOf<McpToolCallResult>()) return null
  if (type.isSubtypeOf(typeOf<Enum<*>>())) return null
  if (type.isSubtypeOf(typeOf<McpToolCallResult>())) return null
  if (type.isSubtypeOf(typeOf<McpToolCallResultContent>())) return null
  val serializer = serializerOrNull(type) ?: return null

  val intermediateJsonSchemaData = initial(type)
    .analyzeTypeUsingKotlinxSerialization()
    .generateJsonSchema()
    .handleCoreAnnotations()
    .handleMcpDescriptionAnnotations(callable)
    .removeNumericBounds()
    .addStringTypeToEnums()

  val schema = intermediateJsonSchemaData.compileInlining()
  val jsonSchema = schema.json.toKt() as? KtJsonObject ?: error("Non-primitive type is expected in return type: ${type.classifier} in $callable")
  val adjustedSchema = removeRequiredForDefaultValues(jsonSchema, serializer)
  val properties = adjustedSchema["properties"] as? KtJsonObject ?: error("Properties are expected in return type: ${type.classifier} in $callable")
  val required = adjustedSchema["required"] as? KtJsonArray ?: error("Required is expected in return type: ${type.classifier} in $callable")
  val requiredProperties = required.map { it.jsonPrimitive.content }.toSet()
  return McpToolSchema.ofPropertiesSchema(properties = properties, requiredProperties = requiredProperties, definitions = emptyMap(), definitionsPath = McpToolSchema.DEFAULT_DEFINITIONS_PATH)
}

private fun JsonNode.toKt(): JsonElement {
  return when (this) {
    is JsonArray -> this.toKtArray()
    is JsonObject -> this.toKtObject()
    is JsonNullValue -> JsonNull
    is JsonBooleanValue -> JsonPrimitive(value)
    is JsonNumericValue -> JsonPrimitive(value)
    is JsonTextValue -> JsonPrimitive(this.value)
  }
}

private fun JsonObject.toKtObject(): KtJsonObject {
  return buildJsonObject {
    for ((key, value) in this@toKtObject.properties) {
      put(key, value.toKt())
    }
  }
}

private fun JsonArray.toKtArray(): KtJsonArray {
  return buildJsonArray {
    for (item in this@toKtArray.items) {
      add(item.toKt())
    }
  }
}

private fun IntermediateJsonSchemaData.handleMcpDescriptionAnnotations(customDescriptionProvider: KAnnotatedElement? = null): IntermediateJsonSchemaData {
  if (customDescriptionProvider != null) {
    // add custom description, usually provided by parameters
    val customAnnotationsData = AnnotationAnalyzer().analyzeAnnotations(customDescriptionProvider.annotations)
    this.rootTypeData.annotations.addAll(customAnnotationsData)
  }
  return JsonSchemaCoreAnnotationMcpDescriptionStep().process(this)
}

// to remove unnecessary bounds that can't be processed by some agents/LLMs
private fun IntermediateJsonSchemaData.removeNumericBounds(): IntermediateJsonSchemaData {
  RemoveNumericBoundsStep().process(this)
  return this
}

private fun IntermediateJsonSchemaData.addStringTypeToEnums(): IntermediateJsonSchemaData {
  AddStringTypeToEnumsStep().process(this)
  return this
}

// to mark properties as optional when they have default values
// fixes problem with EncodeDefault.Never case
// see https://youtrack.jetbrains.com/issue/IJPL-230494
private fun removeRequiredForDefaultValues(schema: KtJsonObject, serializer: KSerializer<*>): KtJsonObject {
  return removeRequiredForDefaultValues(schema, serializer.descriptor) as? KtJsonObject ?: schema
}

private fun removeRequiredForDefaultValues(schema: JsonElement, descriptor: SerialDescriptor): JsonElement {
  val schemaObject = schema as? KtJsonObject ?: return schema
  return buildJsonObject {
    for ((key, value) in schemaObject) {
      when (key) {
        "required" -> put(key, removeOptionalElements(value, descriptor))
        "properties" -> put(key, removeRequiredForDefaultValuesFromProperties(value, descriptor))
        "items" -> put(key, removeRequiredForDefaultValuesFromItems(value, descriptor))
        else -> put(key, value)
      }
    }
  }
}

private fun removeOptionalElements(required: JsonElement, descriptor: SerialDescriptor): JsonElement {
  val requiredArray = required as? KtJsonArray ?: return required
  if (descriptor.elementsCount == 0) return required

  val optionalElements = buildSet {
    for (i in 0 until descriptor.elementsCount) {
      if (descriptor.isElementOptional(i)) add(descriptor.getElementName(i))
    }
  }
  if (optionalElements.isEmpty()) return required

  return buildJsonArray {
    for (element in requiredArray) {
      if (element.jsonPrimitive.content !in optionalElements) add(element)
    }
  }
}

private fun removeRequiredForDefaultValuesFromProperties(properties: JsonElement, descriptor: SerialDescriptor): JsonElement {
  val propertiesObject = properties as? KtJsonObject ?: return properties
  return buildJsonObject {
    for ((name, schema) in propertiesObject) {
      val elementDescriptor = descriptor.getElementDescriptorOrNull(name)
      put(name, if (elementDescriptor == null) schema else removeRequiredForDefaultValues(schema, elementDescriptor))
    }
  }
}

private fun removeRequiredForDefaultValuesFromItems(items: JsonElement, descriptor: SerialDescriptor): JsonElement {
  val elementDescriptor = descriptor.getListElementDescriptorOrNull() ?: return items
  return removeRequiredForDefaultValues(items, elementDescriptor)
}

private fun SerialDescriptor.getElementDescriptorOrNull(name: String): SerialDescriptor? {
  for (i in 0 until elementsCount) {
    if (getElementName(i) == name) return getElementDescriptor(i)
  }
  return null
}

private fun SerialDescriptor.getListElementDescriptorOrNull(): SerialDescriptor? {
  if (elementsCount != 1) return null
  return getElementDescriptor(0)
}

private const val descriptionPropertyNameInschema = "description"

private class JsonSchemaCoreAnnotationMcpDescriptionStep()  {
  fun process(input: IntermediateJsonSchemaData): IntermediateJsonSchemaData {
    input.entries.forEach { process(it, input.typeDataById) }
    return input
  }

  private fun process(schema: JsonSchemaData, typeDataMap: Map<TypeId, TypeData>) {
    val json = schema.json
    if (json is JsonObject && json.properties[descriptionPropertyNameInschema] == null) {
      determineDescription(schema.typeData.annotations)?.let { description ->
        json.properties[descriptionPropertyNameInschema] = JsonTextValue(description)
      }
    }
    iterateProperties(schema, typeDataMap) { prop, propData, propTypeData ->
      determineDescription(propData.annotations + propTypeData.annotations)?.let { description ->
        prop.properties[descriptionPropertyNameInschema] = JsonTextValue(description)
      }
    }
  }

  private fun determineDescription(annotations: Collection<AnnotationData>): String? {
    return annotations
      .filter { it.name == McpDescription::class.qualifiedName }
      .map { (it.values[McpDescription::description.name] as String).trimMargin() }
      .firstOrNull()
  }
}

private class RemoveNumericBoundsStep {
  fun process(input: IntermediateJsonSchemaData): IntermediateJsonSchemaData {
    for (it in input.entries) {
      process(it, input.typeDataById)
    }
    return input
  }

  private fun process(schema: JsonSchemaData, typeDataMap: Map<TypeId, TypeData>) {
    val json = schema.json
    if (json is JsonObject) {
      json.properties.remove("minimum")
      json.properties.remove("maximum")
    }
  }
}

private class AddStringTypeToEnumsStep {
  fun process(input: IntermediateJsonSchemaData): IntermediateJsonSchemaData {
    for (schema in input.entries) {
      process(schema)
    }
    return input
  }

  private fun process(schema: JsonSchemaData) {
    val json = schema.json as? JsonObject ?: return
    if (!schema.typeData.isEnum || json.properties.containsKey("type")) return

    val updatedProperties = LinkedHashMap<String, JsonNode>()
    updatedProperties["type"] = JsonTextValue("string")
    updatedProperties.putAll(json.properties)

    json.properties.clear()
    json.properties.putAll(updatedProperties)
  }
}
