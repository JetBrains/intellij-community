// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.converter

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.util.ImpExpUtils
import java.io.File

class DocxToMarkdownConverterTest: BasePlatformTestCase() {
  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/converter"
  }

  private fun doTest(fileName: String) {
    val expectedMd = myFixture.configureByFile("${fileName}_expected.md").text

    val vFile = VfsUtil.findFileByIoFile(File("$testDataPath/$fileName.docx"), true)!!
    val newFilePath = "${project.basePath!!}/${vFile.name}"
    ImpExpUtils.copyAndConvertToMd(project, vFile, newFilePath)
    val actualMd = File("${project.basePath!!}/$fileName.md").readText()

    assertEquals(expectedMd.trim(), actualMd.trim())
  }

  //fixme: it is necessary to somehow split it into different tests for each file, probably, and that at the same time all the tests pass
  fun testImportDocx() {
    doTest("importSimpleText")
    doTest("importDocWithLink")
    doTest("importDocWithList")
    doTest("importHeaders")
  }
}
