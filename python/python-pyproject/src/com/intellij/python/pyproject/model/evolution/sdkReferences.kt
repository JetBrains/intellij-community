// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID

/** The attribute the Python facet configuration stores its SDK name in. */
private const val FACET_SDK_NAME_ATTRIBUTE = "sdkName"

/**
 * The Python SDK name of [module], read from [storage], or `null`.
 * Order: the module SDK, the project SDK if the module inherits it, the SDK of a Python facet. The module SDK and the
 * project SDK count only when they are Python SDKs. So a Java module with a Python facet gets the facet SDK, and not
 * its JDK, as `PyModuleService.findPythonSdk` answers.
 */
internal fun sdkReferenceOf(module: ModuleEntity, storage: EntityStorage): String? {
  val moduleSdk = module.dependencies.firstNotNullOfOrNull { dependency ->
    when (dependency) {
      is SdkDependency -> dependency.sdk
      is InheritedSdkDependency -> storage.entities<ProjectSettingsEntity>().firstOrNull()?.projectSdk
      else -> null
    }
  }
  if (moduleSdk != null && moduleSdk.type == PyNames.PYTHON_SDK_ID_NAME) return moduleSdk.name
  return module.facets.firstOrNull { it.typeId.name == PYTHON_FACET_ID }?.configurationXmlTag?.let(::facetSdkName)
}

/** The `sdkName` attribute of the facet configuration. A pattern, so this module needs no XML library. */
private val FACET_SDK_NAME = Regex("""\b$FACET_SDK_NAME_ATTRIBUTE\s*=\s*"([^"]*)"""")

private fun facetSdkName(configuration: String): String? =
  FACET_SDK_NAME.find(configuration)?.groupValues?.get(1)?.let(StringUtil::unescapeXmlEntities)?.takeIf { it.isNotEmpty() }
