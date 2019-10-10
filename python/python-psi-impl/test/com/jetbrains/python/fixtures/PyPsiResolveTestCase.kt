package com.jetbrains.python.fixtures

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.TestLocalVirtualFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.testFramework.TestDataFile
import com.jetbrains.python.PythonTestUtil
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException

@NonNls
internal const val MARKER = "<ref>"

open class PyPsiResolveTestCase : PyPsiTestCase() {

  private val myFileSystem = TestLocalVirtualFileSystem()

  protected fun configureByFile(@TestDataFile filePath: String): PsiReference? {
    val testDataRoot = myFileSystem.refreshAndFindFileByPath(PythonTestUtil.getTestDataPath())

    val file = testDataRoot!!.findFileByRelativePath(filePath)
    assertNotNull(file)

    val fileText: String =
      try {
        StringUtil.convertLineSeparators(VfsUtilCore.loadText(file!!))
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }

    val offset = fileText.indexOf(MARKER)
    assertTrue(offset >= 0)
    val finalText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length)
    VfsUtil.saveText(file, finalText)
    val psiFile = PsiManager.getInstance(myProject).findFile(file)
    return psiFile?.findReferenceAt(offset)
  }
}