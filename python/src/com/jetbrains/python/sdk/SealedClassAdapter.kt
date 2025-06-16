// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.google.gson.*
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Type

/**
 * Mark sealed class to serialize automatically each inheritor
 */
@ApiStatus.Internal

class SealedClassAdapter : JsonSerializer<Any>, JsonDeserializer<Any> {
  private companion object {
    const val sealedClassChildId = "sealedClassChildId"
    const val sealedClassData = "data"
  }

  override fun serialize(src: Any, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
    val result = JsonObject()
    result.addProperty(sealedClassChildId, src.javaClass.kotlin.simpleName)
    result.add(sealedClassData, context.serialize(src))
    return result
  }

  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Any {
    val childName = json.asJsonObject.get(sealedClassChildId).asString
    val childClass = (typeOfT as Class<*>).kotlin.sealedSubclasses.first { it.simpleName == childName }
    return context.deserialize(json.asJsonObject.get(sealedClassData), childClass.java)
  }
}