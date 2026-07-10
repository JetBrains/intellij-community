// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.python.pytools.PyTool
import java.util.Properties

/**
 * Pinned versions of the external Python tools (ty, ruff, basedpyright, pyrefly, …) exercised by the
 * env tests, loaded from the single shared `lspTools/toolVersions.properties` resource.
 *
 * Centralising the pins here — instead of hard-coding a version per test — keeps version bumps in one
 * place and lets independent tests install the same, reproducible version. The tests assert
 * version-specific tool behaviour, so they must agree on the version they install.
 */
object LspToolVersions {
  private const val RESOURCE = "/lspTools/toolVersions.properties"

  private val versions: Properties by lazy {
    Properties().apply {
      (LspToolVersions::class.java.getResourceAsStream(RESOURCE)
       ?: error("Missing test resource: $RESOURCE")).use { load(it) }
    }
  }

  /**
   * The pinned PEP 508 requirement (e.g. `ruff==0.15.18`) for [tool], keyed by its PyPI
   * package ([PyTool.packageName]).
   */
  fun requirement(tool: PyTool): String = requirement(tool.packageName.name)

  /** The pinned PEP 508 requirement (e.g. `pandas==3.0.2`) for the PyPI package [pypiPackage]. */
  fun requirement(pypiPackage: String): String {
    val version = versions.getProperty(pypiPackage)
                  ?: error("No pinned version for '$pypiPackage' in $RESOURCE")
    return "$pypiPackage==$version"
  }
}
