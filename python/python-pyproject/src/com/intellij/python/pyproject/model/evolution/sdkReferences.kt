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
import com.intellij.python.sdk.backend.PyInterpreterLibraryName
import com.intellij.python.sdk.backend.pyInterpreterDependency

/** The interpreter library [module] names, see [PyInterpreterLibraryName], or `null` when it names none. */
internal fun interpreterLibraryOf(module: ModuleEntity): PyInterpreterLibraryName? =
  module.dependencies.pyInterpreterDependency?.library?.name?.let(PyInterpreterLibraryName::parse)

/** The attribute the Python facet configuration stores its SDK name in. */
private const val FACET_SDK_NAME_ATTRIBUTE = "sdkName"

/**
 * The Python SDK name that an older build stored for [module], read from [storage], or `null`. In the legacy flow it is
 * what the module stores to name its interpreter, see [storedInterpreterReferenceOf].
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

/**
 * The Python SDK name that [module] names itself, or `null`: its own Python SDK, else the SDK name of its Python facet.
 * An inherited project SDK is not one: a module does not inherit its interpreter. [migrateSdkReferences] gives a module
 * with such a name an interpreter library entry, and the name makes the module a Python project, see [namesInterpreter].
 */
internal fun ownSdkReferenceOf(module: ModuleEntity): String? =
  module.dependencies.firstNotNullOfOrNull { (it as? SdkDependency)?.sdk?.takeIf { sdk -> sdk.type == PyNames.PYTHON_SDK_ID_NAME }?.name }
  ?: module.facets.firstOrNull { it.typeId.name == PYTHON_FACET_ID }?.configurationXmlTag?.let(::facetSdkName)

/** The `sdkName` attribute of the facet configuration. A pattern, so this module needs no XML library. */
private val FACET_SDK_NAME = Regex("""\b$FACET_SDK_NAME_ATTRIBUTE\s*=\s*"([^"]*)"""")

internal fun facetSdkName(configuration: String): String? =
  FACET_SDK_NAME.find(configuration)?.groupValues?.get(1)?.let(StringUtil::unescapeXmlEntities)?.takeIf { it.isNotEmpty() }

/**
 * [configuration] with an empty `sdkName`, so the facet names no SDK. The facet itself writes an empty value for no SDK,
 * so its next save does not change the configuration again.
 */
internal fun withoutFacetSdkName(configuration: String): String =
  FACET_SDK_NAME.replace(configuration, "$FACET_SDK_NAME_ATTRIBUTE=\"\"")
