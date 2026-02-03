// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import com.intellij.serialization.SerializationException
import com.jetbrains.python.Result
import kotlinx.serialization.json.Json


/**
 * A functional interface defining a contract for parsing JSON strings into instances of type [T].
 *
 * Potential exceptions:
 * - [IllegalArgumentException]: Thrown if the input JSON string is invalid or improper.
 * - [SerializationException]: Thrown if there is a failure during the deserialization process.
 *
 * @param T The type of object expected as the result of JSON parsing.
 */
fun interface JsonParser<T> {
  fun parseJson(rawJson: String): T
}


/**
 * Parses the JSON from the process execution result using the provided parser function.
 * [jsonParser] may throw [SerializationException] or [IllegalArgumentException] if output is invalid,
 * in this case the parsing exception localized message will be returned.
 *
 *
 * @param jsonParser Function that converts stdout string to type [T].
 * @return A [Result] containing either the successfully parsed [T] object, or a parsing failure message
 */
class ZeroCodeJsonParserTransformer<T>(jsonParser: JsonParser<T>) : ZeroCodeStdoutParserTransformer<T>(
  {
    try {
      Result.success(jsonParser.parseJson(it))
    }
    catch (t: SerializationException) {
      Result.failure(t.localizedMessage)
    }
    catch (t: IllegalArgumentException) {
      Result.failure(t.localizedMessage)
    }
  }
) {
  companion object {
    inline operator fun <reified T> invoke(json: Json): ZeroCodeJsonParserTransformer<T> {
      return ZeroCodeJsonParserTransformer(JsonParser { json.decodeFromString(it) })
    }
  }
}