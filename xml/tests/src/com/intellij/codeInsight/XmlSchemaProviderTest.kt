// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.utils.vfs.getPsiFile
import com.intellij.xml.XmlSchemaProvider

class XmlSchemaProviderTest : BasePlatformTestCase() {

  fun testSingleFileSource() {
    XmlSchemaProvider.EP_NAME.point.registerExtension(TestXmlSchemaProvider(), testRootDisposable)

    val testXml = runWriteActionAndWait { createSingleFileSource() }

    try {
      XmlSchemaProvider.findSchema("foo", testXml.getPsiFile(project))
    }
    finally {
      runWriteActionAndWait {
        testXml.delete(this)
      }
    }
  }

  private fun createSingleFileSource(): VirtualFile {
    val tmpRoot = checkNotNull(VirtualFileManager.getInstance().findFileByUrl("temp:///"))
    tmpRoot.refresh(false, false)

    val testXml = tmpRoot.createFile("test.xml")

    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      model.addContentEntry(testXml)
    }
    waitUntilIndexesAreReady(module.project)

    return testXml
  }

  private class TestXmlSchemaProvider : XmlSchemaProvider() {
    override fun getSchema(url: String, module: Module?, baseFile: PsiFile): XmlFile? {
      assertNotNull(module)
      return null
    }

    override fun isAvailable(file: XmlFile): Boolean = true
  }
}
