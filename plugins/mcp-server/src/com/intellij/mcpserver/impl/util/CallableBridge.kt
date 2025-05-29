package com.intellij.mcpserver.impl.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy

class CallableBridge(private val callable: KCallable<*>, private val thisRef: Any? = null, private val json: Json = Json) {
  class Result(val result: Any?, val resultSerializer: KSerializer<Any?>, private val json: Json) {
    fun encodeToString(): String = json.encodeToString(resultSerializer, result)
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
    serializerOrNull(callable.returnType) ?: throw SerializationException("Return type ${callable.returnType} is not serializable")
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

    val result = callable.callSuspendBy(argMap)
    val resultSerializer = serializerOrNull(callable.returnType) ?: error("Return type ${callable.returnType} is not serializable")
    return Result(result, resultSerializer, json)
  }
}