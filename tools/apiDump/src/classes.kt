// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump

class API(
  val publicApi: List<ApiClass>,
  val privateApi: Set<String>,
)

data class ApiClass(
  val className: String,
  val flags: ApiFlags,
  val supers: List<String>,
  val members: List<ApiMember>, // sort order: fields before methods, name, descriptor
)

/**
 * Method or field
 */
data class ApiMember(
  val ref: ApiRef,
  val flags: ApiFlags,
)

data class ApiRef(
  val name: String,
  val descriptor: String,
)

data class ApiFlags(
  val access: Int,
  val annotationExperimental: Boolean,
)
