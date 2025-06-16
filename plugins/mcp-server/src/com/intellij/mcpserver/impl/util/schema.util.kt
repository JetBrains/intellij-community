package com.intellij.mcpserver.impl.util

import com.intellij.mcpserver.McpToolInputSchema
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
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter


fun KCallable<*>.parametersSchema(): McpToolInputSchema {
  val parameterSchemas = mutableMapOf<String, JsonElement>()
  val definitions = mutableMapOf<String, JsonElement>()
  val requiredParameters = mutableSetOf<String>()

  for (parameter in this.parameters) {
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
  return McpToolInputSchema(parameters = parameterSchemas, requiredParameters = requiredParameters, definitions = definitions, definitionsPath = McpToolInputSchema.DEFAULT_DEFINITIONS_PATH)
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

private class JsonSchemaCoreAnnotationMcpDescriptionStep()  {
  fun process(input: IntermediateJsonSchemaData): IntermediateJsonSchemaData {
    input.entries.forEach { process(it, input.typeDataById) }
    return input
  }

  private fun process(schema: JsonSchemaData, typeDataMap: Map<TypeId, TypeData>) {
    val json = schema.json
    if (json is JsonObject && json.properties["description"] == null) {
      determineDescription(schema.typeData.annotations)?.let { description ->
        json.properties["description"] = JsonTextValue(description)
      }
    }
    iterateProperties(schema, typeDataMap) { prop, propData, propTypeData ->
      determineDescription(propData.annotations + propTypeData.annotations)?.let { description ->
        prop.properties["description"] = JsonTextValue(description)
      }
    }
  }

  private fun determineDescription(annotations: Collection<AnnotationData>): String? {
    return annotations
      .filter { it.name == McpDescription::class.qualifiedName }
      .map { it.values["description"] as String }
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