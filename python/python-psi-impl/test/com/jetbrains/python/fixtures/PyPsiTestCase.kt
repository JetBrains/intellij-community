package com.jetbrains.python.fixtures

import com.intellij.core.CoreFileTypeRegistry
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.IdeaTestApplication
import com.intellij.mock.MockApplication
import com.intellij.mock.MockFileTypeManager
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.MockPsiApplication
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.impl.MockPsiProject
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataFile
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonTestUtil.getTestDataPath
import junit.framework.TestCase
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException

open class PyPsiTestCase : TestCase() {
  private val myParentDisposable: Disposable = TestDisposable()
  private val myFileTypeManager = MockFileTypeManager(PythonFileType.INSTANCE)
  private val myApplication: MockPsiApplication = MockPsiApplication(myParentDisposable)
    .also { ApplicationManager.setApplication(it, Getter { myFileTypeManager }, myParentDisposable) }
  protected val myProject: MockPsiProject = MockPsiProject(myApplication)

  init {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "PythonPsi")
    val loadedPlugins = PluginManagerCore.getLoadedPlugins(PyPsiTestCase::class.java.classLoader)
    myApplication.registerComponents(loadedPlugins)
    myApplication.picoContainer.registerComponentInstance(FileTypeManager::class.java, myFileTypeManager)
    myProject.registerComponents(loadedPlugins)
  }

  protected fun getTestName(lowercaseFirstLetter: Boolean): String =
    name?.let { PlatformTestUtil.getTestName(it, lowercaseFirstLetter) } ?: ""

  inner class TestDisposable: Disposable {
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