package com.jetbrains.python.fixtures

import com.intellij.core.CoreFileTypeRegistry
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.MockPsiApplication
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.impl.MockPsiProject
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PythonFileType
import junit.framework.TestCase

fun getTestName(name: String, lowercaseFirstLetter: Boolean): String {
  var name = name
  name = StringUtil.trimStart(name, "test")
  return if (StringUtil.isEmpty(name)) "" else lowercaseFirstLetter(name, lowercaseFirstLetter)
}

fun lowercaseFirstLetter(name: String, lowercaseFirstLetter: Boolean): String {
  var name = name
  if (lowercaseFirstLetter && !isAllUppercaseName(name)) {
    name = Character.toLowerCase(name[0]) + name.substring(1)
  }
  return name
}

fun isAllUppercaseName(name: String): Boolean {
  var uppercaseChars = 0
  for (i in 0 until name.length) {
    if (Character.isLowerCase(name[i])) {
      return false
    }
    if (Character.isUpperCase(name[i])) {
      uppercaseChars++
    }
  }
  return uppercaseChars >= 3
}

open class PyPsiTestCase : TestCase() {
  private val myParentDisposable: Disposable = TestDisposable()
  private val myFileTypeManager = CoreFileTypeRegistry()
  private val myApplication: MockPsiApplication = MockPsiApplication(myParentDisposable)
    .also { ApplicationManager.setApplication(it, Getter { myFileTypeManager }, myParentDisposable) }
  protected val myProject: MockPsiProject = MockPsiProject(myApplication)

  init {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "PythonPsi")
    val loadedPlugins = PluginManagerCore.getLoadedPlugins(PyPsiTestCase::class.java.classLoader)
    myApplication.registerComponents(loadedPlugins)
    myApplication.picoContainer.registerComponentInstance(FileTypeRegistry::class.java, myFileTypeManager)
    myProject.registerComponents(loadedPlugins)
    myFileTypeManager.registerFileType(PythonFileType.INSTANCE, "py")
  }

  protected fun getTestName(lowercaseFirstLetter: Boolean): String =
    name?.let { getTestName(it, lowercaseFirstLetter) } ?: ""

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
