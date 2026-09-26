// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.metaInfo

import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiFile
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.TestResourcePathResolver
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfoData
import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfo
import com.intellij.python.junit5Tests.framework.metaInfo.WithCustomTestResourcePathResolver
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@WithCustomTestResourcePathResolver(TestMetaInfoExtensionTest.ComposedResolver::class)
private annotation class WithComposedCustomResolver

@PyDefaultTestApplication
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath($$"$CONTENT_ROOT/../junit5Tests-framework/testResources/example/junit5")
class TestMetaInfoExtensionTest {
  @Test
  @TestMetaInfo(resourcePath = "should-not-be-used.py")
  @WithComposedCustomResolver
  fun testComposedCustomResolver(
    psiFile: PsiFile,
  ) = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      Assertions.assertEquals("print(\"Custom path\")\n", psiFile.text)
    }
  }

  object ComposedResolver : TestResourcePathResolver {
    override fun resolve(resourcePath: String, context: ExtensionContext, testName: String, classInfo: TestClassInfoData): String {
      return "custom/main.py"
    }
  }
}
