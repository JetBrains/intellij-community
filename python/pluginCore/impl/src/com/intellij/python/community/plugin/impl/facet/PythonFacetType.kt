// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.facet

import com.intellij.facet.Facet
import com.intellij.facet.FacetType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.facet.defaultPythonFacetSdk
import com.jetbrains.python.parser.icons.PythonParserIcons
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * The Python facet of the legacy flow: it holds the Python SDK of a module of another type, such as a Java module of
 * IntelliJ IDEA or a module of GoLand. IntelliJ IDEA and the other IDEs with the Python plugin share it. PyCharm has none.
 *
 * The interpreter library flow has no Python facet. The interpreter library entry of a module names its interpreter, and
 * the migration of an older project removes the facet, see `migrateSdkReferences`.
 */
@OptIn(PyInternalExecApi::class)
@ApiStatus.Internal
class PythonFacetType : FacetType<PythonFacet, PythonFacetConfiguration>(PythonFacet.ID, PYTHON_FACET_ID, PyBundle.message("python.facet.name")) {
  override fun createDefaultConfiguration(): PythonFacetConfiguration =
    PythonFacetConfiguration().apply { sdk = defaultPythonFacetSdk() }

  override fun createFacet(module: Module, name: String, configuration: PythonFacetConfiguration, underlyingFacet: Facet<*>?): PythonFacet =
    PythonFacet(this, module, name, configuration, underlyingFacet)

  override fun isSuitableModuleType(moduleType: ModuleType<*>): Boolean = moduleType.id != PYTHON_MODULE_ID

  override fun getIcon(): Icon = PythonParserIcons.PythonFile

  companion object {
    @JvmStatic
    fun getInstance(): PythonFacetType = findInstance(PythonFacetType::class.java)
  }
}
