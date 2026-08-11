// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.maven.testFramework.fixtures

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.openapi.vfs.VfsUtilCore
import org.junit.Assert.assertNotNull

// Facet inspection helpers, ported from FacetImporterTestCase.

fun MavenTestFixture.doAssertSourceRoots(actualRoots: List<String>, vararg roots: String) {
  val expectedRootUrls = roots.map { VfsUtilCore.pathToUrl("$projectPath/$it") }
  assertUnorderedPathsAreEqual(actualRoots, expectedRootUrls)
}

fun <F : Facet<*>> MavenTestFixture.findFacet(
  module: String,
  type: FacetType<F, *>,
  facetName: String = type.defaultFacetName,
): F? {
  return FacetManager.getInstance(getModule(module)).findFacet(type.id, facetName)
}

fun <F : Facet<*>> MavenTestFixture.getFacet(
  module: String,
  type: FacetType<F, *>,
  facetName: String = type.defaultFacetName,
): F? {
  val result = findFacet(module, type, facetName)
  assertNotNull("facet '$type:$facetName' not found", result)
  return result
}
