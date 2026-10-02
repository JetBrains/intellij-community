// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(PyInternalExecApi::class)

package com.intellij.python.junit5Tests.unit.evolution

import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityTypeId
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.pyproject.model.evolution.sdkReferenceOf
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Which SDK name the Python project structure reads for a module. The module SDK and the inherited project SDK count
 * only when they are Python SDKs, so a Java module with a Python facet gets the facet SDK and not its JDK.
 */
internal class SdkReferenceOfTest {
  private val source = NonPersistentEntitySource
  private val pythonSdk = SdkId("Python 3.12", PyNames.PYTHON_SDK_ID_NAME)
  private val jdk = SdkId("JDK 21", "JavaSDK")
  @Test
  fun `a Python module gets its Python SDK`() {
    val storage = storage(dependencies = listOf(SdkDependency(pythonSdk)))

    assertThat(sdkReferenceOf(storage.module(), storage)).isEqualTo(pythonSdk.name)
  }

  @Test
  fun `a module with only a Python facet gets the facet SDK`() {
    val storage = storage(dependencies = emptyList(), facetSdkName = pythonSdk.name)

    assertThat(sdkReferenceOf(storage.module(), storage)).isEqualTo(pythonSdk.name)
  }

  @Test
  fun `a Java module with its own JDK and a Python facet gets the facet SDK`() {
    val storage = storage(dependencies = listOf(SdkDependency(jdk)), facetSdkName = pythonSdk.name)

    assertThat(sdkReferenceOf(storage.module(), storage)).isEqualTo(pythonSdk.name)
  }

  @Test
  fun `a Java module that inherits a JDK and has a Python facet gets the facet SDK`() {
    val storage = storage(dependencies = listOf(InheritedSdkDependency), facetSdkName = pythonSdk.name, projectSdk = jdk)

    assertThat(sdkReferenceOf(storage.module(), storage)).isEqualTo(pythonSdk.name)
  }

  @Test
  fun `a module that inherits a Python project SDK gets it`() {
    val storage = storage(dependencies = listOf(InheritedSdkDependency), projectSdk = pythonSdk)

    assertThat(sdkReferenceOf(storage.module(), storage)).isEqualTo(pythonSdk.name)
  }

  @Test
  fun `a Java module without a Python facet gets no SDK`() {
    val storage = storage(dependencies = listOf(SdkDependency(jdk)))

    assertThat(sdkReferenceOf(storage.module(), storage)).isNull()
  }

  private fun storage(
    dependencies: List<ModuleDependencyItem>,
    facetSdkName: String? = null,
    projectSdk: SdkId? = null,
  ): MutableEntityStorage = MutableEntityStorage.create().apply {
    projectSdk?.let { addEntity(ProjectSettingsEntity(source) { this.projectSdk = it }) }
    addEntity(ModuleEntity("module", dependencies + ModuleSourceDependency, source) {
      if (facetSdkName != null) {
        moduleSettings = listOf(FacetEntity("Python", FacetEntityTypeId(PYTHON_FACET_ID), source) {
          configurationXmlTag = """<configuration sdkName="$facetSdkName" />"""
        })
      }
    })
  }

  private fun MutableEntityStorage.module(): ModuleEntity = entities<ModuleEntity>().single()
}
