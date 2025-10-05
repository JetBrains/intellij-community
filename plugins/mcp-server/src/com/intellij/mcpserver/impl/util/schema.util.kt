package com.intellij.mcpserver.impl.util

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
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.*
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonArray
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonObject
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.analyzeTypeUsingKotlinxSerialization
import io.github.smiley4.schemakenerator.serialization.analyzer.AnnotationAnalyzer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf


fun KCallable<*>.parametersSchema(): McpToolSchema {
  val parameterSchemas = mutableMapOf<String, JsonElement>()
  val definitions = mutableMapOf<String, JsonElement>()
  val requiredParameters = mutableSetOf<String>()

  // probably passthrough something like `additionalImplicitParameters` from outsise
  // but it isn't neccessary right now
  for (parameter in this.parameters + projectPathParameter) {
    if (parameter.kind != KParameter.Kind.VALUE) continue

    val parameterName = parameter.name ?: error("Parameter has no name: ${parameter.name} in $this")

    val parameterType = parameter.type

    val intermediateJsonSchemaData = initial(parameterType)
      .analyzeTypeUsingKotlinxSerialization()
      .generateJsonSchema()
      .handleCoreAnnotations()
      .handleMcpDescriptionAnnotations(parameter)
      .removeNumericBounds()

    val schema = intermediateJsonSchemaData.compileInlining()

    parameterSchemas[parameterName] = schema.json.toKt()
    for ((key, def) in schema.definitions) {
      definitions[key] = def.toKt() // overwrite definitions because types can be the same across different parameters
    }
    if (!parameter.isOptional) requiredParameters.add(parameterName)
  }
  return McpToolSchema.ofPropertiesMap(properties = parameterSchemas, requiredProperties = requiredParameters, definitions = definitions, definitionsPath = McpToolSchema.DEFAULT_DEFINITIONS_PATH)
}

private fun projectPathParameterStub(
  @McpDescription("""
    | The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls. 
    | In the case you know only the current working directory you can use it as the project path.
    | If you're not aware about the project path you can ask user about it.""")
  projectPath: String? = null) {}
private val projectPathParameter: KParameter get() = ::projectPathParameterStub.parameters.single()
internal val projectPathParameterName: String get() = projectPathParameter.name ?: error("Parameter has no name: ${projectPathParameter.name}")

fun KCallable<*>.returnTypeSchema(): McpToolSchema? {
  val type = this.returnType
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
  if (serializerOrNull(type) == null) return null

  val intermediateJsonSchemaData = initial(type)
    .analyzeTypeUsingKotlinxSerialization()
    .generateJsonSchema()
    .handleCoreAnnotations()
    .handleMcpDescriptionAnnotations(this)
    .removeNumericBounds()

  val schema = intermediateJsonSchemaData.compileInlining()
  val jsonSchema = schema.json.toKt() as? kotlinx.serialization.json.JsonObject ?: error("Non-primitive type is expected in return type: ${type.classifier} in $this")
  val properties = jsonSchema["properties"] as? kotlinx.serialization.json.JsonObject ?: error("Properties are expected in return type: ${type.classifier} in $this")
  val required = jsonSchema["required"] as? kotlinx.serialization.json.JsonArray ?: error("Required is expected in return type: ${type.classifier} in $this")
  return McpToolSchema.ofPropertiesSchema(properties = properties, requiredProperties = required.map { it.jsonPrimitive.content }.toSet(), definitions = emptyMap(), definitionsPath = McpToolSchema.DEFAULT_DEFINITIONS_PATH)
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

private fun JsonObject.toKtObject(): kotlinx.serialization.json.JsonObject {
  return buildJsonObject {
    for ((key, value) in this@toKtObject.properties) {
      put(key, value.toKt())
    }
  }
}

private fun JsonArray.toKtArray(): kotlinx.serialization.json.JsonArray {
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