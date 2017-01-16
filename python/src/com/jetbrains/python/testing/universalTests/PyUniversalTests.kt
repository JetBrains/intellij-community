/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.jetbrains.python.testing.universalTests

import com.google.gson.Gson
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.impl.PyPsiFacadeImpl
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.CommandLinePatcher
import com.jetbrains.python.run.PythonConfigurationFactoryBase
import com.jetbrains.python.testing.*
import com.jetbrains.reflection.DelegationProperty
import com.jetbrains.reflection.Properties
import com.jetbrains.reflection.getProperties
import org.jdom.Element
import java.io.File
import java.util.*
import javax.swing.JComponent


/**
 * New (universal) API for test runners.
 *
 * @author Ilya.Kazakevich
 */


fun isUniversalModeEnabled(): Boolean = Registry.`is`("python.tests.enableUniversalTests")

internal fun getAdditionalArgumentsPropertyName() = PyUniversalTestConfiguration::additionalArguments.name

/**
 * Resolves qname of any symbol to appropriate PSI element.
 */
private fun findElementByQualifiedName(name: QualifiedName, module: Module, context: TypeEvalContext): PsiElement? {
  val facade = PyPsiFacadeImpl.getInstance(module.project)
  var currentName = name


  var element: PsiElement? = null

  // Drill as deep, as we can
  var lastElement: String? = null
  while (currentName.componentCount > 0 && element == null) {

    element = facade.qualifiedNameResolver(currentName).fromModule(module).withMembers().firstResult()
    if (element != null) {
      break
    }
    lastElement = name.lastComponent!!
    currentName = name.removeLastComponent()
  }

  if (lastElement != null && element is PyClass) {
    // Drill in class
    val method = element.findMethodByName(lastElement, true, context)
    if (method != null) {
      return method
    }

  }
  return element
}

/**
 * @return factory chosen by user in "test runner" settings
 */
private fun findConfigurationFactoryFromSettings(module: Module): ConfigurationFactory {
  val name = TestRunnerService.getInstance(module).projectConfiguration
  val factories = PyUniversalTestsConfigurationType.configurationFactories
  val configurationFactory = factories.find { it.name == name }
  return configurationFactory ?: factories.first()
}


private object PyUniversalTestsLocator : SMTestLocator {
  override fun getLocation(protocol: String, path: String, project: Project, scope: GlobalSearchScope): MutableList<Location<PsiElement>> {
    if (scope !is ModuleWithDependenciesScope) {
      return ArrayList()
    }
    val result = findElementByQualifiedName(QualifiedName.fromDottedString(path), scope.module,
                                            TypeEvalContext.userInitiated(project, null))
    if (result != null) {
      return arrayListOf(PsiLocation.fromPsiElement(result))
    }
    else {
      return ArrayList()
    }
  }
}

abstract class PyUniversalTestExecutionEnvironment<T : PyUniversalTestConfiguration>(configuration: T, environment: ExecutionEnvironment)
  : PythonTestCommandLineStateBase<T>(configuration, environment) {

  override fun getTestLocator(): SMTestLocator = PyUniversalTestsLocator

  override fun getTestSpecs(): MutableList<String> = ArrayList(configuration.getTestSpec())

  override fun generateCommandLine(patchers: Array<out CommandLinePatcher>?): GeneralCommandLine {
    val line = super.generateCommandLine(patchers)
    line.workDirectory = File(configuration.workingDirectorySafe)
    return line
  }
}


abstract class PyUniversalTestSettingsEditor(private val form: PyUniversalTestForm)
  : SettingsEditor<PyUniversalTestConfiguration>() {


  override fun resetEditorFrom(s: PyUniversalTestConfiguration) {
    // usePojoProperties is true because we know that Form is java-based
    AbstractPythonRunConfiguration.copyParams(s, form.optionsForm)
    s.copyTo(getProperties(form, usePojoProperties = true))
  }

  override fun applyEditorTo(s: PyUniversalTestConfiguration) {
    AbstractPythonRunConfiguration.copyParams(form.optionsForm, s)
    s.copyFrom(getProperties(form, usePojoProperties = true))
  }

  override fun createEditor(): JComponent = form.panel
}

enum class TestTargetType(val optionName: String) {
  PYTHON("--target"), FOLDER("--path"), CUSTOM("")
}


/**
 * Target depends on target type. It could be path to file/folder or python target
 */
data class ConfigurationTarget(@ConfigField var target: String, @ConfigField var targetType: TestTargetType) {
  fun copyTo(dst: ConfigurationTarget) {
    // TODO:  do we have such method it in Kotlin?
    dst.target = target
    dst.targetType = targetType
  }
}

/**
 * Parent of all new test configurations.
 * All config-specific fields are implemented as properties. They are saved/restored automatically and passed to GUI form.
 *
 * @param runBareFunctions if config supports running functions directly in modules or only class methods
 */
abstract class PyUniversalTestConfiguration(project: Project,
                                            configurationFactory: ConfigurationFactory,
                                            private val runBareFunctions: Boolean = true)
  : AbstractPythonTestRunConfiguration<PyUniversalTestConfiguration>(project, configurationFactory), PyRerunAwareConfiguration {
  @DelegationProperty
  val target = ConfigurationTarget(".", TestTargetType.FOLDER)
  @ConfigField
  var additionalArguments = ""

  val testFrameworkName = configurationFactory.name!!


  private fun getTestSpecForPythonTarget(location: Location<*>): List<String> {
    if (location !is PsiLocation) {
      return emptyList()
    }
    if (location.psiElement !is PyQualifiedNameOwner) {
      return emptyList()
    }
    val qualifiedName = (location.psiElement as PyQualifiedNameOwner).qualifiedName ?: return emptyList()
    return listOf(TestTargetType.PYTHON.optionName, qualifiedName)
  }

  override fun getTestSpec(location: Location<*>, failedTest: AbstractTestProxy): String? {
    val list = getTestSpecForPythonTarget(location)
    if (list.isEmpty()) {
      return null
    }
    else {
      return list.joinToString(" ")
    }
  }

  override fun getTestSpecsForRerun(scope: GlobalSearchScope,
                                    locations: MutableList<Pair<Location<*>, AbstractTestProxy>>): List<String> {
    val result = ArrayList<String>()
    locations.map { getTestSpecForPythonTarget(it.first) }.filterNotNull().forEach { result.addAll(it) }
    return result + generateRawArguments()
  }

  fun getTestSpec() = listOf(target.targetType.optionName, target.target) + generateRawArguments()

  /**
   * raw arguments to be added after "--" and passed to runner directly
   */
  private fun generateRawArguments(): List<String> {
    val rawArguments = additionalArguments + " " + getCustomRawArgumentsString()
    if (rawArguments.isNotBlank()) {
      return listOf("--") + rawArguments.trim().split(" ")
    }
    return emptyList()
  }

  /**
   * @return configuration-specific arguments
   */
  protected open fun getCustomRawArgumentsString() = ""

  fun reset() {
    target.target = "."
    target.targetType = TestTargetType.FOLDER
    additionalArguments = ""
  }

  fun copyFrom(src: Properties) {
    src.copyTo(getConfigFields())
  }

  fun copyTo(dst: Properties) {
    getConfigFields().copyTo(dst)
  }


  override fun writeExternal(element: Element) {
    super.writeExternal(element)

    val gson = Gson()

    getConfigFields().properties.forEach {
      JDOMExternalizerUtil.writeField(element, it.getName(), gson.toJson(it.get()))
    }
  }

  override fun readExternal(element: Element) {
    super.readExternal(element)

    val gson = Gson()

    getConfigFields().properties.forEach {
      val fromJson: Any? = gson.fromJson(JDOMExternalizerUtil.readField(element, it.getName()), it.getType())
      if (fromJson != null) {
        it.set(fromJson)
      }
    }
  }


  private fun getConfigFields() = getProperties(this, ConfigField::class.java)

  /**
   * Checks if element could be test target for this config.
   * Function is used to create tests by context.
   *
   * If yes, and element is [PsiElement] then it is [TestTargetType.PYTHON].
   * If file then [TestTargetType.FOLDER]
   */
  fun couldBeTestTarget(element: PsiElement) =
    // TODO: PythonUnitTestUtil logic is weak. We should give user ability to launch test on symbol since user knows better if folder
    // contains tests etc
    when (element) {
      is PyFile -> PythonUnitTestUtil.isUnitTestFile(element)
      is PyFunction -> PythonUnitTestUtil.isTestCaseFunction(element, runBareFunctions)
      is PyClass -> PythonUnitTestUtil.isTestCaseClass(element, TypeEvalContext.userInitiated(element.project, element.containingFile))
      else -> false
    }
}

object PyUniversalTestsConfigurationType : PythonTestConfigurationType() {
  override fun getId() = "py_universal_tests"

  override fun getConfigurationFactories(): Array<ConfigurationFactory> {
    if (isUniversalModeEnabled()) {
      return arrayOf(PyUniversalUnitTestFactory,
                     PyUniversalPyTestFactory,
                     PyUniversalNoseTestFactory)
    }
    // Array can't be empty according to contract (type is fetched from first element)
    return arrayOf(PyUniversalUnitTestFactory)
  }
}

abstract class PyUniversalTestFactory : PythonConfigurationFactoryBase(PyUniversalTestsConfigurationType)

/**
 * Only one producer is registered with EP, but it uses factory configured by user to prdouce different configs
 */
object PyUniversalTestsConfigurationProducer : RunConfigurationProducer<PyUniversalTestConfiguration>(PyUniversalTestsConfigurationType) {

  override fun cloneTemplateConfiguration(context: ConfigurationContext): RunnerAndConfigurationSettings {
    return cloneTemplateConfigurationStatic(context, findConfigurationFactoryFromSettings(context.module))
  }

  override fun findOrCreateConfigurationFromContext(context: ConfigurationContext?): ConfigurationFromContext? {
    if (!isUniversalModeEnabled()) {
      return null
    }
    return super.findOrCreateConfigurationFromContext(context)
  }

  override fun setupConfigurationFromContext(configuration: PyUniversalTestConfiguration?,
                                             context: ConfigurationContext?,
                                             sourceElement: Ref<PsiElement>?): Boolean {

    if (sourceElement == null || configuration == null) {
      return false
    }
    val nameAndTarget = getNameAndTargetForConfig(configuration, sourceElement.get()) ?: return false
    configuration.name = nameAndTarget.first
    nameAndTarget.second.copyTo(configuration.target)
    return true
  }


  // TODO: DOC
  private fun getNameAndTargetForConfig(configuration: PyUniversalTestConfiguration,
                                        baseElement: PsiElement): Pair<String, ConfigurationTarget>? {
    var element = baseElement
    // Go up until we reach top of the file
    // asking configuration about each element if it is supported or not
    // If element is supported -- set it as configuration target
    do {
      if (configuration.couldBeTestTarget(element)) {
        when (element) {
          is PyQualifiedNameOwner -> { // Function, class, method
            val qualifiedName = element.qualifiedName
            if (qualifiedName == null) {
              Logger.getInstance(PyUniversalTestConfiguration::class.java).warn("$element has no qualified name")
              return null
            }
            return Pair(qualifiedName, ConfigurationTarget(qualifiedName, TestTargetType.PYTHON))
          }
          is PsiFile -> return Pair(element.virtualFile.name, ConfigurationTarget(element.virtualFile.path, TestTargetType.FOLDER))
        }
      }
      element = element.parent
    }
    while (element !is PsiDirectory) // if parent is folder, then we are at file level
    return null
  }


  override fun isConfigurationFromContext(configuration: PyUniversalTestConfiguration?, context: ConfigurationContext?): Boolean {
    val psiElement = context?.psiLocation ?: return false
    val nameAndTarget = getNameAndTargetForConfig(configuration!!, psiElement) ?: return false
    return configuration.target == nameAndTarget.second
  }

  override fun isPreferredConfiguration(self: ConfigurationFromContext?, other: ConfigurationFromContext?): Boolean {
    if (self == null || other == null) {
      return false
    }
    val module = ModuleUtil.findModuleForPsiElement(self.sourceElement) ?: return false
    return self.configuration.factory == findConfigurationFactoryFromSettings(module)
  }
}


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
/**
 * Mark run configuration field with it to enable saving, resotring and form iteraction
 */
annotation class ConfigField
