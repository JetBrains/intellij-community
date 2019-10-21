package com.jetbrains.python.fixtures

import com.intellij.core.CoreFileTypeRegistry
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.MockPsiApplication
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.impl.MockPsiProject
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.TestLocalVirtualFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import junit.framework.TestCase
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NonNls
import java.io.IOException

@NonNls
internal const val MARKER = "<ref>"
@NonNls
internal const val CARET = "<caret>"

fun getTestName(name: String, lowercaseFirstLetter: Boolean): String {
  val trimmedName = StringUtil.trimStart(name, "test")
  return if (StringUtil.isEmpty(trimmedName)) "" else lowercaseFirstLetter(trimmedName, lowercaseFirstLetter)
}

fun lowercaseFirstLetter(name: String, lowercaseFirstLetter: Boolean): String =
  if (lowercaseFirstLetter && !isAllUppercaseName(name)) {
    Character.toLowerCase(name[0]) + name.substring(1)
  }
  else name

fun isAllUppercaseName(name: String): Boolean {
  var uppercaseChars = 0
  for (i in name.indices) {
    if (Character.isLowerCase(name[i])) {
      return false
    }
    if (Character.isUpperCase(name[i])) {
      uppercaseChars++
    }
  }
  return uppercaseChars >= 3
}

@Contract("null, _ -> fail")
fun <T> assertInstanceOf(o: Any?, aClass: Class<T>): T {
  TestCase.assertNotNull("Expected instance of: " + aClass.name + " actual: " + null, o)
  requireNotNull(o)
  TestCase.assertTrue("Expected instance of: " + aClass.name + " actual: " + o.javaClass.name, aClass.isInstance(o))
  return o as T
}

open class PyPsiTestCase : TestCase() {
  private val myParentDisposable: Disposable = TestDisposable()
  private val myFileTypeManager = CoreFileTypeRegistry()
  private val myApplication: MockPsiApplication = MockPsiApplication(myParentDisposable)
    .also { ApplicationManager.setApplication(it, Getter { myFileTypeManager }, myParentDisposable) }
  protected val myProject: MockPsiProject = MockPsiProject(myApplication)
  protected val myFileSystem = TestLocalVirtualFileSystem()
  protected var myFile: VirtualFile? = null
  protected var myPsiFile: PsiFile? = null

  init {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "PythonAnalysisTool")
    val loadedPlugins = PluginManagerCore.getLoadedPlugins(PyPsiTestCase::class.java.classLoader)
    myApplication.registerComponents(loadedPlugins)
    myApplication.picoContainer.registerComponentInstance(FileTypeRegistry::class.java, myFileTypeManager)
    myProject.registerComponents(loadedPlugins)
    myFileTypeManager.registerFileType(PythonFileType.INSTANCE, "py")
  }

  protected fun getTestName(lowercaseFirstLetter: Boolean): String =
    name?.let { getTestName(it, lowercaseFirstLetter) } ?: ""

  private fun setLanguageLevel(languageLevel: LanguageLevel?) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myProject, languageLevel)
  }

  protected fun runWithLanguageLevel(languageLevel: LanguageLevel, runnable: () -> Unit) {
    setLanguageLevel(languageLevel)
    try {
      runnable()
    }
    finally {
      setLanguageLevel(null)
    }
  }

  protected fun configureByFileAndFindReference(filePath: String): PsiReference? {
    myFile = getFile(filePath)
    val file = requireNotNull(myFile) { "File by path $filePath not found" }
    val fileText = readFileText(file)

    val offset = fileText.indexOf(MARKER)
    assertTrue(offset >= 0)
    val finalText = fileText.substring(0, offset) + fileText.substring(offset + MARKER.length)
    VfsUtil.saveText(file, finalText)
    myPsiFile = PsiManager.getInstance(myProject).findFile(file)
    return myPsiFile?.findReferenceAt(offset)
  }

  protected open fun configureByFile(filePath: String): PsiFile? {
    myFile = getFile(filePath)
    val file = requireNotNull(myFile) { "File by path $filePath not found" }
    myPsiFile = PsiManager.getInstance(myProject).findFile(file)
    return myPsiFile
  }

  protected fun configureByText(fileType: FileType, text: String): PsiFile? {
    val extension = fileType.defaultExtension
    val fileName = "aaa.$extension"
    myFile = myFileSystem.createTextVirtualFile(fileName, text)
    val file = requireNotNull(myFile) { "Created text file is null" }
    myPsiFile = PsiManager.getInstance(myProject).findFile(file)
    return myPsiFile
  }

  protected fun getFile(filePath: String): VirtualFile? {
    val testDataRoot = myFileSystem.refreshAndFindFileByPath(PythonTestUtil.getTestDataPath())
    return testDataRoot!!.findFileByRelativePath(filePath)
  }

  protected fun readFileText(file: VirtualFile): String =
    try {
      StringUtil.convertLineSeparators(VfsUtilCore.loadText(file))
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }

  private fun findMarkerOffset(psiFile: PsiFile): Int {
    val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)!!
    var offset = -1
    for (i in 1 until document.lineCount) {
      val lineStart = document.getLineStartOffset(i)
      val lineEnd = document.getLineEndOffset(i)
      val index = document.charsSequence.subSequence(lineStart, lineEnd).toString().indexOf("<ref>")
      if (index > 0) {
        offset = document.getLineStartOffset(i - 1) + index
      }
    }
    assertTrue("<ref> in test file not found", offset >= 0)
    return offset
  }

  protected fun findReferenceByMarker(psiFile: PsiFile): PsiReference? {
    val ref = psiFile.findReferenceAt(findMarkerOffset(psiFile))
    assertNotNull("No reference found at <ref> position", ref)
    return ref
  }

  inner class TestDisposable : Disposable {
    @Volatile
    private var myDisposed: Boolean = false

    override fun dispose() {
      myDisposed = true
    }

    override fun toString(): String {
      val testName = getTestName(false)
      return this@PyPsiTestCase.javaClass.name + if (StringUtil.isEmpty(testName)) "" else ".test$testName"
    }
  }
}
