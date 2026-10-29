// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

/** Returns a distribution of the index, by default with the label `//build:<name>_dist`. */
internal fun candidate(
  name: String,
  product: String = "idea",
  additionalModules: List<String> = emptyList(),
  bundledModules: List<String> = emptyList(),
  platformBundledModules: Map<String, List<String>> = emptyMap(),
  runtimeModuleRepository: Boolean = false,
  modularLoader: Boolean = false,
  ultimate: Boolean = false,
  label: String = "//build:${name}_dist",
): DevDistCandidate = DevDistCandidate(
  name = name,
  label = label,
  product = product,
  additionalModules = additionalModules,
  bundledModules = bundledModules,
  platformBundledModules = platformBundledModules,
  runtimeModuleRepository = runtimeModuleRepository,
  modularLoader = modularLoader,
  ultimate = ultimate,
)
