// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.AstLoadingFilter
import com.intellij.util.PsiIconUtil
import com.intellij.util.ThrowableRunnable
import java.util.function.Supplier

class XmlIconsAstLoadingTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()

    Registry.get("ast.loading.filter").setValue(true, testRootDisposable);
  }

  /**
   * If this test fails for your [com.intellij.ide.IconProvider] you MUST avoid loading PSI by either:
   * - indexing and accessing index instead from [com.intellij.ide.IconProvider];
   * - caching computation on AST, e.g., using [com.intellij.util.gist.GistAstMarker].
   */
  fun testNoAstLoadedFromIconProviders() {
    val file = myFixture.addFileToProject("pom.xml", """
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.mycompany.app</groupId>
        <artifactId>my-module</artifactId>
        <version>1</version>
      </project>
    """.trimIndent())

    AstLoadingFilter.disallowTreeLoading(ThrowableRunnable {
      PsiIconUtil.getIconFromProviders(file, 0)
    }, Supplier { "IconProvider must not access PSI of files directly! Use either indexes or GistManager to cache computation" })
  }
}