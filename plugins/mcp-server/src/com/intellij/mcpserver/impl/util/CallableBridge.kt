package com.intellij.mcpserver.impl.util

import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializerOrNull
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspendBy

class CallableBridge(private val callable: KCallable<*>, private val thisRef: Any? = null, private val json: Json = Json) {
  class Result(val result: Any?, private val resultType: KType, private val json: Json) {
    fun encodeToString(): String {
      val serializer = serializerOrNull(resultType) ?: error("Result type ${result} is not serializable")
      return json.encodeToString(serializer, result)
    }

    fun encodeToJson(): JsonObject? {
      val serializer = serializerOrNull(resultType) ?: error("Result type ${result} is not serializable")
      val jsonObject = json.encodeToJsonElement(serializer, result) as? JsonObject
      thisLogger().assertTrue(jsonObject != null, "Result must be a JSON object")
      return jsonObject
    }
  }

  init {
    ensureSerializable()
  }

  private fun ensureSerializable() {
    for (parameter in callable.parameters) {
      if (parameter.kind == KParameter.Kind.VALUE) {
        serializerOrNull(parameter.type) ?: throw SerializationException("Type ${parameter.type} of parameter '${parameter.name}' is not serializable")
      }
    }
  }

  suspend fun call(args: JsonObject): Result {
    val argMap = mutableMapOf<KParameter, Any?>()
    for (parameter in callable.parameters) {
      if (parameter.kind == KParameter.Kind.INSTANCE) {
        argMap[parameter] = thisRef ?: error("Instance parameter is null")
        continue
      }
      val argElement = args[parameter.name]
      if (argElement == null && parameter.isOptional) continue
      if (argElement == null) error("No argument is passed for required parameter '${parameter.name}'")
      val serializer = serializerOrNull(parameter.type) ?: error("Parameter '${parameter.name}' is not serializable and is not provided explicitly")
      val decodedArg = json.decodeFromJsonElement(serializer, argElement)
      argMap[parameter] = decodedArg
    }

    val result = try {
      callable.callSuspendBy(argMap)
    }
    catch (e: InvocationTargetException) {
      throw e.cause ?: e
    }
    return Result(result, callable.returnType, json)
  }
}