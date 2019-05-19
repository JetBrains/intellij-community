// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.google.common.base.Preconditions
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.run.PythonConfigurationFactoryBase
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration.TestType
import com.jetbrains.python.testing.nosetestLegacy.PythonNoseTestRunConfiguration
import com.jetbrains.python.testing.pytestLegacy.PyTestRunConfiguration
import com.jetbrains.python.testing.unittestLegacy.PythonUnitTestRunConfiguration
import org.jdom.Element
import javax.swing.SwingUtilities

/**
 * Module to support legacy configurations.
 *
 * When legacy configuration is ought to be dropped, just remove this module and all references to it.
 * It supports switching back to old runners (see [isNewTestsModeEnabled]) and importing old configs to new one.
 * [projectInitialized] shall be called for that.
 *
 * @author Ilya.Kazakevich
 */

/**
 * @return is new mode enabled or not
 */
fun isNewTestsModeEnabled(): Boolean = Registry.`is`("python.tests.enableUniversalTests", true)

private class PyTestLegacyInteropInitializer : ApplicationInitializedListener {
  override fun componentsInitialized() {
    ApplicationManager.getApplication().messageBus.connect().subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
      override fun stateLoaded(runManager: RunManager, isFirstLoadState: Boolean) {
        if (!isFirstLoadState) {
          return
        }

        fun migrate(configuration: RunConfiguration) {
          (configuration as? PyAbstractTestConfiguration ?: return).legacyConfigurationAdapter.copyFromLegacyIfNeeded()
        }

        for (factory in pythonFactories) {
          migrate(runManager.getConfigurationTemplate(factory).configuration)
        }
        for (it in runManager.allSettings) {
          migrate(it.configuration)
        }
      }
    })
  }
}

private fun getVirtualFileByPath(path: String): VirtualFile? {
  return LocalFileSystem.getInstance().findFileByPath(path) ?: return null
}

private fun VirtualFile.asPyFile(project: Project): PyFile? {
  assert(project.isInitialized) { "This function can't be used on uninitialized project" }
  if (this.isDirectory) {
    return null
  }
  var file: PyFile? = null

  ApplicationManager.getApplication()
    .invokeAndWait({
                     val document = FileDocumentManager.getInstance().getDocument(this)
                     if (document != null) {
                       file = PyUtil.`as`(PsiDocumentManager.getInstance(project).getPsiFile(document), PyFile::class.java)
                     }
                   })
  return file
}

/**
 * Manages legacy-to-new configuration binding
 * Attach it to new configuration and mark with [com.jetbrains.reflection.DelegationProperty]
 */
class PyTestLegacyConfigurationAdapter<in T : PyAbstractTestConfiguration>(newConfig: T) : JDOMExternalizable {
  private val configManager: LegacyConfigurationManager<*, *>?
  private val project = newConfig.project

  /**
   * Does configuration contain legacy information or was it created as new config?
   * Null is unknown
   */
  private var containsLegacyInformation: Boolean? = null

  /**
   * True if configuration [containsLegacyInformation] and this information is already copied to new config, so it should not be
   * copied second time.
   *
   * Null means "false" and used here to prevent saving useless "false" value in .xml for new configurations.
   */
  @ConfigField
  var legacyInformationCopiedToNew: Boolean? = null

  init {
    when (newConfig) {
      is PyTestConfiguration -> {
        configManager = LegacyConfigurationManagerPyTest(newConfig)
      }
      is PyNoseTestConfiguration -> {
        configManager = LegacyConfigurationManagerNose(newConfig)
      }
      is PyUnitTestConfiguration -> {
        configManager = LegacyConfigurationManagerUnit(newConfig)
      }
      else -> {
        configManager = null
      }
    }
  }

  override fun readExternal(element: Element) {
    val configManager = configManager
    if (configManager == null) {
      containsLegacyInformation = false
      return
    }
    val legacyConfig = configManager.legacyConfig
    if (legacyConfig is RunConfiguration) {
      (legacyConfig as RunConfiguration).readExternal(element)
    }
    else {
      (legacyConfig as JDOMExternalizable).readExternal(element)
    }

    containsLegacyInformation = configManager.isLoaded()
    if (project.isInitialized) {
      copyFromLegacyIfNeeded()
    }
  }

  override fun writeExternal(element: Element) {
    if (containsLegacyInformation ?: return) {
      val legacyConfig = configManager?.legacyConfig
      if (legacyConfig is RunConfiguration) {
        (legacyConfig as RunConfiguration).writeExternal(element)
      }
      else {
        (legacyConfig as JDOMExternalizable).writeExternal(element)
      }
    }
  }

  fun copyFromLegacyIfNeeded() {
    assert(project.isInitialized, { "Initialized project required" })
    if (containsLegacyInformation ?: return && !(legacyInformationCopiedToNew ?: false) && SwingUtilities.isEventDispatchThread()) {
        configManager?.copyFromLegacy()
        legacyInformationCopiedToNew = true
      }
  }
}

/**
 * Manages legacy-to-new configuration copying process
 */
private abstract class LegacyConfigurationManager<
  LEGACY_CONF_T : AbstractPythonLegacyTestRunConfiguration<LEGACY_CONF_T>,
  out NEW_CONF_T : PyAbstractTestConfiguration
  >(legacyConfFactory: PythonConfigurationFactoryBase, val newConfig: NEW_CONF_T) {

  @Suppress("UNCHECKED_CAST") // Factory-to-config mapping should be checked by developer: createTemplateConfiguration is not generic
  val legacyConfig = legacyConfFactory.createTemplateConfiguration(newConfig.project) as LEGACY_CONF_T

  /**
   * Checks test type to interpret target correctly. It could be function, class or method
   */
  private fun getElementFromConfig(script: PyFile): PyQualifiedNameOwner? {
    if (legacyConfig.testType == TestType.TEST_FUNCTION) {
      return script.findTopLevelFunction(legacyConfig.methodName)
    }
    val clazz = script.findTopLevelClass(legacyConfig.className) ?: return null
    if (legacyConfig.testType == TestType.TEST_CLASS) {
      return clazz
    }
    return clazz.findMethodByName(legacyConfig.methodName, true, TypeEvalContext.userInitiated(legacyConfig.project, script))
  }

  /**
   * If one of these fields is not empty -- legacy configuration makes sence
   */
  protected open fun getFieldsToCheckForEmptiness() = listOf(legacyConfig.scriptName, legacyConfig.className, legacyConfig.methodName)

  /**
   * @return true of legacy configuration loaded, false if configuration is pure new
   */
  fun isLoaded() = getFieldsToCheckForEmptiness().find { !it.isNullOrBlank() } != null

  /**
   * This method should be called from AWT thread only
   *
   * Copies config from legacy to new configuration.
   * Used by all runners but pytest which has very different settings
   */
  open fun copyFromLegacy() {
    Preconditions.checkState(SwingUtilities.isEventDispatchThread(), "Run on AWT thread only")
    when (legacyConfig.testType) {
      TestType.TEST_CLASS, TestType.TEST_FUNCTION, TestType.TEST_METHOD -> {
        val virtualFile = getVirtualFileByPath(legacyConfig.scriptName) ?: return
        val pyFile = virtualFile.asPyFile(legacyConfig.project) ?: return
        val qualifiedName = getElementFromConfig(pyFile)?.qualifiedName ?: return
        newConfig.target.targetType = PyRunTargetVariant.PYTHON
        newConfig.target.target = qualifiedName
      }
      TestType.TEST_FOLDER -> {
        newConfig.target.targetType = PyRunTargetVariant.PATH
        newConfig.target.target = legacyConfig.folderName
      }
      TestType.TEST_SCRIPT -> {
        newConfig.target.targetType = PyRunTargetVariant.PATH
        newConfig.target.target = legacyConfig.scriptName
      }
      else -> {
        Logger.getInstance(LegacyConfigurationManager::class.java).warn("Unknown type {${legacyConfig.testType}")
      }
    }
  }
}


private class LegacyConfigurationManagerPyTest(newConfig: PyTestConfiguration) :
  LegacyConfigurationManager<PyTestRunConfiguration, PyTestConfiguration>(
    PythonTestConfigurationType.getInstance().LEGACY_PYTEST_FACTORY, newConfig) {
  /**
   * In Pytest target is provided as keywords, joined with "and".
   * "function_foo", "MyClass" or "MyClass and my_method" could be used here.
   */
  private val KEYWORDS_SPLIT_PATTERN = java.util.regex.Pattern.compile("\\s+and\\s+", java.util.regex.Pattern.CASE_INSENSITIVE)

  override fun getFieldsToCheckForEmptiness() = super.getFieldsToCheckForEmptiness() + listOf(legacyConfig.keywords, legacyConfig.testToRun)

  override fun copyFromLegacy() {
    // Do not call parent since target is always provided as testToRun here
    newConfig.additionalArguments = legacyConfig.params

    // Default is PATH
    newConfig.target.targetType = PyRunTargetVariant.PATH

    val oldKeywords = legacyConfig.keywords

    val virtualFile = getVirtualFileByPath(legacyConfig.testToRun) ?: return
    if (virtualFile.isDirectory) {
      // If target is directory, then it can't point to any symbol
      newConfig.target.target = virtualFile.path
      newConfig.target.targetType = PyRunTargetVariant.PATH
      newConfig.keywords = oldKeywords
      return
    }
    // If it is file -- it could be file, class, method or functions (see keywords)
    val script = virtualFile.asPyFile(newConfig.project) ?: return


    val keywordsList = oldKeywords.split(KEYWORDS_SPLIT_PATTERN).filter(String::isNotEmpty)
    if (keywordsList.isEmpty() || keywordsList.size > 2 || keywordsList.find { it.contains(" ") } != null) {
      //Give up with interpreting
      newConfig.keywords = oldKeywords
      newConfig.target.target = script.virtualFile.path
      newConfig.target.targetType = PyRunTargetVariant.PATH
      return
    }
    val classOrFunctionName = keywordsList[0]
    val clazz = script.findTopLevelClass(classOrFunctionName)

    if (keywordsList.size == 1) { // Class or function
      val classOrFunction = PyUtil.`as`(clazz ?:
                                        script.findTopLevelFunction(classOrFunctionName),
                                        PyQualifiedNameOwner::class.java) ?: return
      newConfig.target.target = classOrFunction.qualifiedName ?: return
      newConfig.target.targetType = PyRunTargetVariant.PYTHON
    }
    if (keywordsList.size == 2) { // Class and method
      clazz ?: return
      val method = clazz.findMethodByName(keywordsList[1], true, TypeEvalContext.userInitiated(newConfig.project, script)) ?: return
      newConfig.target.target = method.qualifiedName ?: return
      newConfig.target.targetType = PyRunTargetVariant.PYTHON

    }

  }
}

private class LegacyConfigurationManagerUnit(newConfig: PyUnitTestConfiguration) :
  LegacyConfigurationManager<PythonUnitTestRunConfiguration, PyUnitTestConfiguration>(
    PythonTestConfigurationType.getInstance().LEGACY_UNITTEST_FACTORY, newConfig) {
  override fun copyFromLegacy() {
    super.copyFromLegacy()
    newConfig.additionalArguments = legacyConfig.params
    newConfig.pattern = legacyConfig.pattern
  }
}


private class LegacyConfigurationManagerNose(newConfig: PyNoseTestConfiguration) :
  LegacyConfigurationManager<PythonNoseTestRunConfiguration, PyNoseTestConfiguration>(
    PythonTestConfigurationType.getInstance().LEGACY_NOSETEST_FACTORY, newConfig) {
  override fun copyFromLegacy() {
    super.copyFromLegacy()
    newConfig.additionalArguments = legacyConfig.params
  }
}
