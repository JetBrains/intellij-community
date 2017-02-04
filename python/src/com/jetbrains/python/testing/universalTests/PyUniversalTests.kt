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
import com.intellij.execution.RunManager
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
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.QualifiedName
import com.jetbrains.extenstions.toElement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.CommandLinePatcher
import com.jetbrains.python.run.PythonConfigurationFactoryBase
import com.jetbrains.python.testing.*
import com.jetbrains.reflection.DelegationProperty
import com.jetbrains.reflection.Properties
import com.jetbrains.reflection.Property
import com.jetbrains.reflection.getProperties
import org.jdom.Element
import java.io.File
import java.util.*
import javax.swing.JComponent


/**
 * New configuration factories
 */
val factories: Array<PythonConfigurationFactoryBase> = arrayOf(PyUniversalUnitTestFactory,
                                                               PyUniversalPyTestFactory,
                                                               PyUniversalNoseTestFactory)

internal fun getAdditionalArgumentsPropertyName() = PyUniversalTestConfiguration::additionalArguments.name

/**
 * Since runners report names of tests as qualified name, no need to convert it to PSI and back to string.
 * We just save its name and provide it again to rerun
 */
private class PyTargetBasedPsiLocation(val target: ConfigurationTarget, element: PsiElement) : PsiLocation<PsiElement>(element)


/**
 * @return factory chosen by user in "test runner" settings
 */
private fun findConfigurationFactoryFromSettings(module: Module): ConfigurationFactory {
  val name = TestRunnerService.getInstance(module).projectConfiguration
  val factories = PythonTestConfigurationType.getInstance().configurationFactories
  val configurationFactory = factories.find { it.name == name }
  return configurationFactory ?: factories.first()
}


private object PyUniversalTestsLocator : SMTestLocator {
  override fun getLocation(protocol: String, path: String, project: Project, scope: GlobalSearchScope): List<Location<out PsiElement>> {
    if (scope !is ModuleWithDependenciesScope) {
      return listOf()
    }
    val element = QualifiedName.fromDottedString(path).toElement(scope.module,
                                                                 TypeEvalContext.userInitiated(project, null))
    if (element != null) {
      // Path is qualified name of python test according to runners protocol
      return listOf(PyTargetBasedPsiLocation(ConfigurationTarget(path, TestTargetType.PYTHON), element))
    }
    else {
      return listOf()
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
  PYTHON("--target"), PATH("--path"), CUSTOM("")
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
 * To prevent legacy configuration options  from clashing with new names, we add prefix
 * to use for writing/reading xml
 */
private val Property.prefixedName: String
  get() = "_new_" + this.getName()

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
  val target = ConfigurationTarget(".", TestTargetType.PATH)
  @ConfigField
  var additionalArguments = ""

  val testFrameworkName = configurationFactory.name!!

  @Suppress("LeakingThis") // Legacy adapter is used to support legacy configs. Leak is ok here since everything takes place in one thread
  @DelegationProperty
  val legacyConfigurationAdapter = PyUniversalTestLegacyConfigurationAdapter(this)


  override fun isTestBased() = true

  private fun getTestSpecForPythonTarget(location: Location<*>): List<String> {

    if (location is PyTargetBasedPsiLocation) {
      return listOf(location.target.targetType.optionName, location.target.target)
    }

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

  fun getTestSpec(): List<String> {
    // For custom we only need to provide additional (raw) args
    // Provide target otherwise
    if (target.targetType == TestTargetType.CUSTOM) {
      return generateRawArguments()
    }
    return listOf(target.targetType.optionName, target.target) + generateRawArguments()
  }

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
    target.targetType = TestTargetType.PATH
    additionalArguments = ""
  }

  fun copyFrom(src: Properties) {
    src.copyTo(getConfigFields())
  }

  fun copyTo(dst: Properties) {
    getConfigFields().copyTo(dst)
  }


  override fun writeExternal(element: Element) {
    // Write legacy config to preserve it
    legacyConfigurationAdapter.writeExternal(element)
    // Super is called after to overwrite legacy settings with new one
    super.writeExternal(element)

    val gson = Gson()

    getConfigFields().properties.forEach {
      val value = it.get()
      if (value != null) {
        // No need to write null since null is default value
        JDOMExternalizerUtil.writeField(element, it.prefixedName, gson.toJson(value))
      }
    }
  }

  override fun readExternal(element: Element) {
    super.readExternal(element)

    val gson = Gson()

    getConfigFields().properties.forEach {
      val fromJson: Any? = gson.fromJson(JDOMExternalizerUtil.readField(element, it.prefixedName), it.getType())
      if (fromJson != null) {
        it.set(fromJson)
      }
    }
    legacyConfigurationAdapter.readExternal(element)
  }


  private fun getConfigFields() = getProperties(this, ConfigField::class.java)

  /**
   * Checks if element could be test target for this config.
   * Function is used to create tests by context.
   *
   * If yes, and element is [PsiElement] then it is [TestTargetType.PYTHON].
   * If file then [TestTargetType.PATH]
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

abstract class PyUniversalTestFactory<out CONF_T : PyUniversalTestConfiguration> : PythonConfigurationFactoryBase(
  PythonTestConfigurationType.getInstance()) {
  override abstract fun createTemplateConfiguration(project: Project): CONF_T
}

/**
 * Only one producer is registered with EP, but it uses factory configured by user to prdouce different configs
 */
object PyUniversalTestsConfigurationProducer : AbstractPythonTestConfigurationProducer<PyUniversalTestConfiguration>(
  PythonTestConfigurationType.getInstance()) {

  override val configurationClass = PyUniversalTestConfiguration::class.java

  override fun cloneTemplateConfiguration(context: ConfigurationContext): RunnerAndConfigurationSettings {
    return cloneTemplateConfigurationStatic(context, findConfigurationFactoryFromSettings(context.module))
  }

  override fun createConfigurationFromContext(context: ConfigurationContext?): ConfigurationFromContext? {
    // Since we need module, no need to even try to create config with out of it
    context?.module ?: return null
    return super.createConfigurationFromContext(context)
  }

  override fun findOrCreateConfigurationFromContext(context: ConfigurationContext?): ConfigurationFromContext? {
    if (!isNewTestsModeEnabled()) {
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

    val location = context?.location
    if (location is PyTargetBasedPsiLocation) {
      configuration.name = location.target.target
      location.target.copyTo(configuration.target)
      return true
    }

    configuration.name = nameAndTarget.first
    nameAndTarget.second.copyTo(configuration.target)
    return true
  }

  /**
   * Find concrete element to be used as test target.
   * @return configuration name and its target
   */
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
          is PsiFile -> return Pair(element.virtualFile.name, ConfigurationTarget(element.virtualFile.path, TestTargetType.PATH))
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
