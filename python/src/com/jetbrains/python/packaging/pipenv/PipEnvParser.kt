// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pipenv

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.jetbrains.python.packaging.PyPackage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PipEnvParser {
  @ApiStatus.Internal
  data class GraphPackage(
    @SerializedName("key") var key: String,
    @SerializedName("package_name") var packageName: String,
    @SerializedName("installed_version") var installedVersion: String,
    @SerializedName("required_version") var requiredVersion: String?,
  )

  @ApiStatus.Internal
  data class GraphEntry(
    @SerializedName("package") var pkg: GraphPackage,
    @SerializedName("dependencies") var dependencies: List<GraphPackage>,
  )

  /**
   * Parses the output of `pipenv graph --json` into a list of GraphEntries.
   */
  fun parsePipEnvGraph(input: String): List<GraphEntry> = try {
    Gson().fromJson(input, Array<GraphEntry>::class.java)?.toList() ?: emptyList()
  }
  catch (e: JsonSyntaxException) {
    // TODO: Log errors
    emptyList()
  }


  /**
   * Parses the list of GraphEntries into a list of packages.
   */
  fun parsePipEnvGraphEntries(input: List<GraphEntry>): List<PyPackage> {
    return input
      .asSequence()
      .flatMap { sequenceOf(it.pkg) + it.dependencies.asSequence() }
      .map { PyPackage(it.packageName, it.installedVersion) }
      .distinct()
      .toList()
  }

}