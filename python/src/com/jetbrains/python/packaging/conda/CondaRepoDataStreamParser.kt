// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.Result
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
import java.io.InputStream

internal object CondaRepoDataStreamParser {
  private const val PACKAGES_FIELD = "packages"
  private const val PACKAGES_CONDA_FIELD = "packages.conda"
  private const val NAME_FIELD = "name"
  private const val VERSION_FIELD = "version"
  private val JSON_FACTORY = JsonFactory()

  @RequiresBackgroundThread
  fun parseVersions(inputStream: InputStream): Result<PackageVersionMap, ParseError> =
    try {
      JSON_FACTORY.createParser(ObjectReadContext.empty(), inputStream).use { parser ->
        require(parser.nextToken() == JsonToken.START_OBJECT) { "repodata.json: expected top-level object" }

        val accumulator = mutableMapOf<String, MutableSet<String>>()

        while (parser.nextToken() != JsonToken.END_OBJECT) {
          val field = parser.currentName()
          parser.nextToken()

          if (field != PACKAGES_FIELD && field != PACKAGES_CONDA_FIELD) {
            // ignoring "info", "removed" and "repodata_version"
            parser.skipChildren()
            continue
          }

          parsePackagesMap(parser, accumulator)
        }

        Result.Success(accumulator)
      }
    }
    catch (e: JacksonException) {
      Result.Failure(ParseError(e.localizedMessage))
    }

  @RequiresBackgroundThread
  private fun parsePackagesMap(parser: JsonParser, accumulator: MutableMap<String, MutableSet<String>>) {
    require(parser.currentToken() == JsonToken.START_OBJECT)

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      // field name is the build filename, e.g. "numpy-1.26.0-py311hXXX.conda"
      parser.nextToken() // START_OBJECT of the record

      var name: String? = null
      var version: String? = null

      while (parser.nextToken() != JsonToken.END_OBJECT) {
        parser.nextToken()

        when (parser.currentName()) {
          NAME_FIELD -> {
            name = parser.string
          }
          VERSION_FIELD -> {
            version = parser.string
          }
          else -> {
            parser.skipChildren()
          }
        }

        if (name != null && version != null) {
          // name and version were parsed, skipping the rest of the record
          while (parser.nextToken() != JsonToken.END_OBJECT) {
            parser.skipChildren()
          }

          break
        }
      }

      if (name != null && version != null) {
        accumulator.getOrPut(name) { mutableSetOf() }.add(version)
      }
    }
  }

  data class ParseError(val message: @NlsSafe String)
}