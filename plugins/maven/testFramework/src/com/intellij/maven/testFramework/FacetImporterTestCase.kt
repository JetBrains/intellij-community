// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.facet.*
import com.intellij.openapi.vfs.VfsUtilCore

abstract class FacetImporterTestCase<FACET, C : FacetConfiguration> : MavenMultiVersionImportingTestCase() where FACET : Facet<C> {
  protected abstract fun getFacetTypeId(): FacetTypeId<FACET>

  protected fun doAssertSourceRoots(actualRoots: List<String>, vararg roots: String) {
    val expectedRootUrls: MutableList<String> = ArrayList<String>()

    for (r in roots) {
      val url = VfsUtilCore.pathToUrl("$projectPath/$r")
      expectedRootUrls.add(url)
    }

    assertUnorderedPathsAreEqual(actualRoots, expectedRootUrls)
  }

  protected fun getFacet(module: String): FACET? {
    return getFacet<FACET>(module, this.facetType)
  }

  protected fun findFacet(module: String): FACET? {
    return findFacet<FACET>(module, this.facetType)
  }

  protected fun <T : Facet<*>> findFacet(module: String, type: FacetType<T, *>): T? {
    return findFacet<T>(module, type, this.getDefaultFacetName())
  }

  protected fun <T : Facet<*>> findFacet(module: String, type: FacetType<T, *>, facetName: String): T? {
    val manager = FacetManager.getInstance(getModule(module))
    return manager.findFacet<T>(type.getId(), facetName)
  }

  protected fun <T : Facet<*>> getFacet(module: String, type: FacetType<T, *>): T? {
    val result = findFacet<T>(module, type)
    assertNotNull("facet '$type' not found", result)
    return result
  }

  protected fun <T : Facet<*>> getFacet(module: String, type: FacetType<T, *>, facetName: String): T? {
    val result = findFacet<T>(module, type, facetName)
    assertNotNull("facet '$type:$facetName' not found", result)
    return result
  }

  private val facetType: FacetType<FACET, C>
    get() = FacetTypeRegistry.getInstance().findFacetType(getFacetTypeId())

  protected open fun getDefaultFacetName(): String = this.facetType.defaultFacetName
}
