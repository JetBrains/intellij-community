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


package com.jetbrains.python.testing

import com.intellij.execution.ExecutionException
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil.readField
import com.intellij.openapi.util.JDOMExternalizerUtil.writeField
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.QualifiedName
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.util.ThreeState
import com.jetbrains.extensions.asPsiElement
import com.jetbrains.extensions.asVirtualFile
import com.jetbrains.extensions.getQName
import com.jetbrains.extensions.isWellFormed
import com.jetbrains.extenstions.ModuleBasedContextAnchor
import com.jetbrains.extenstions.QNameResolveContext
import com.jetbrains.extenstions.getElementAndResolvableName
import com.jetbrains.extenstions.resolveToElement
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.run.*
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.run.targetBasedConfiguration.TargetWithVariant
import com.jetbrains.python.run.targetBasedConfiguration.createRefactoringListenerIfPossible
import com.jetbrains.python.run.targetBasedConfiguration.targetAsPsiElement
import com.jetbrains.reflection.DelegationProperty
import com.jetbrains.reflection.Properties
import com.jetbrains.reflection.Property
import com.jetbrains.reflection.getProperties
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.TestStdErr
import jetbrains.buildServer.messages.serviceMessages.TestStdOut
import java.util.regex.Matcher


/**
 * New configuration factories
 */
val factories: Array<PythonConfigurationFactoryBase> = arrayOf(
  PyUnitTestFactory,
  PyTestFactory,
  PyNoseTestFactory,
  PyTrialTestFactory)

/**
 * Accepts text that may be wrapped in TC message. Unwarps it and removes TC escape code.
 * Regular text is unchanged
 */
fun processTCMessage(text: String): String {
  val parsedMessage = ServiceMessage.parse(text.trim()) ?: return text // Not a TC message
  return when (parsedMessage) {
    is TestStdOut -> parsedMessage.stdOut // TC with stdout
    is TestStdErr -> parsedMessage.stdErr // TC with stderr
    else -> "" // TC with out of any output
  }
}

internal fun getAdditionalArgumentsPropertyName() = com.jetbrains.python.testing.PyAbstractTestConfiguration::additionalArguments.name

/**
 * If runner name is here that means test runner only can run inheritors for TestCase
 */
val RunnersThatRequireTestCaseClass: Set<String> = setOf<String>(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME,
                                                                 PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.TRIAL_TEST))

/**
 * Checks if element could be test target
 * @param testCaseClassRequired see [PythonUnitTestUtil] docs
 */
fun isTestElement(element: PsiElement, testCaseClassRequired: ThreeState, typeEvalContext: TypeEvalContext): Boolean = when (element) {
  is PyFile -> PythonUnitTestUtil.isTestFile(element, testCaseClassRequired, typeEvalContext)
  is com.intellij.psi.PsiDirectory -> element.name.contains("test", true) || element.children.any {
    it is PyFile && PythonUnitTestUtil.isTestFile(it, testCaseClassRequired, typeEvalContext)
  }
  is PyFunction -> PythonUnitTestUtil.isTestFunction(element,
                                                     testCaseClassRequired, typeEvalContext)
  is com.jetbrains.python.psi.PyClass -> {
    PythonUnitTestUtil.isTestClass(element, testCaseClassRequired, typeEvalContext)
  }
  else -> false
}


/**
 * Since runners report names of tests as qualified name, no need to convert it to PSI and back to string.
 * We just save its name and provide it again to rerun
 * @param metainfo additional info provided by test runner, in case of py.test it is test name that could be used as "-k" argument
 */
private class PyTargetBasedPsiLocation(val target: ConfigurationTarget,
                                       element: PsiElement,
                                       val metainfo: String?) : PsiLocation<PsiElement>(element) {
  override fun equals(other: Any?): Boolean {
    if (other is PyTargetBasedPsiLocation) {
      return target == other.target && metainfo == other.metainfo
    }
    return false
  }

  override fun hashCode(): Int {
    return target.hashCode()
  }
}


/**
 * @return factory chosen by user in "test runner" settings
 */
private fun findConfigurationFactoryFromSettings(module: Module): ConfigurationFactory {
  val name = TestRunnerService.getInstance(module).projectConfiguration
  val factories = PythonTestConfigurationType.getInstance().configurationFactories
  val configurationFactory = factories.find { it.name == name }
  return configurationFactory ?: factories.first()
}


// folder provided by python side. Resolve test names versus it
private val PATH_URL = java.util.regex.Pattern.compile("^python<([^<>]+)>$")


/**
 * Resolves url into element
 */
fun getElementByUrl(url: String,
                    module: Module,
                    evalContext: TypeEvalContext): Location<out PsiElement>? {
  val protocol = VirtualFileManager.extractProtocol(url) ?: return null
  return getElementByUrl(protocol,
                         VirtualFileManager.extractPath(url),
                         module,
                         evalContext)
}

private fun getElementByUrl(protocol: String,
                            path: String,
                            module: Module,
                            evalContext: TypeEvalContext,
                            matcher: Matcher = PATH_URL.matcher(protocol),
                            metainfo: String? = null): Location<out PsiElement>? {
  val folder = if (matcher.matches()) {
    LocalFileSystem.getInstance().findFileByPath(matcher.group(1))
  }
  else {
    null
  }

  val qualifiedName = QualifiedName.fromDottedString(path)
  // Assume qname id good and resolve it directly
  val element = qualifiedName.resolveToElement(QNameResolveContext(ModuleBasedContextAnchor(module),
                                                                   evalContext = evalContext,
                                                                   folderToStart = folder,
                                                                   allowInaccurateResult = true))
  return if (element != null) {
    // Path is qualified name of python test according to runners protocol
    // Parentheses are part of generators / parametrized tests
    // Until https://github.com/JetBrains/teamcity-messages/issues/121 they are disabled,
    // so we cut them out of path not to provide unsupported targets to runners
    val pathNoParentheses = QualifiedName.fromComponents(
      qualifiedName.components.filter { !it.contains('(') }).toString()
    PyTargetBasedPsiLocation(ConfigurationTarget(pathNoParentheses, PyRunTargetVariant.PYTHON), element, metainfo)
  }
  else {
    null
  }
}


object PyTestsLocator : SMTestLocator {

  override fun getLocation(protocol: String,
                           path: String,
                           metainfo: String?,
                           project: Project,
                           scope: GlobalSearchScope): List<Location<out PsiElement>> = getLocationInternal(protocol, path, project, metainfo, scope)

  override fun getLocation(protocol: String, path: String, project: Project, scope: GlobalSearchScope): List<Location<out PsiElement>> {
    return getLocationInternal(protocol, path, project, null, scope)
  }

  private fun getLocationInternal(protocol: String,
                                  path: String,
                                  project: Project,
                                  metainfo: String?,
                                  scope: GlobalSearchScope): List<Location<out PsiElement>> {
    if (scope !is ModuleWithDependenciesScope) {
      return listOf()
    }
    val matcher = PATH_URL.matcher(protocol)
    if (!matcher.matches()) {
      // special case: setup.py runner uses unittest configuration but different (old) protocol
      // delegate to old protocol locator until setup.py moved to separate configuration
      val oldLocation = PythonUnitTestTestIdUrlProvider.INSTANCE.getLocation(protocol, path, project, scope)
      if (oldLocation.isNotEmpty()) {
        return oldLocation
      }
    }

    return getElementByUrl(protocol, path, scope.module, TypeEvalContext.codeAnalysis(project, null), matcher, metainfo)?.let {
      listOf(it)
    } ?: listOf()
  }
}


abstract class PyTestExecutionEnvironment<T : PyAbstractTestConfiguration>(configuration: T,
                                                                           environment: ExecutionEnvironment)
  : PythonTestCommandLineStateBase<T>(configuration, environment) {

  override fun getTestLocator(): SMTestLocator = PyTestsLocator

  override fun getTestSpecs(): MutableList<String> = java.util.ArrayList(configuration.getTestSpec())

  override fun generateCommandLine(): GeneralCommandLine {
    val line = super.generateCommandLine()
    line.workDirectory = java.io.File(configuration.workingDirectorySafe)
    return line
  }
}


abstract class PyAbstractTestSettingsEditor(private val sharedForm: PyTestSharedForm)
  : SettingsEditor<PyAbstractTestConfiguration>() {


  override fun resetEditorFrom(s: PyAbstractTestConfiguration) {
    // usePojoProperties is true because we know that Form is java-based
    AbstractPythonRunConfiguration.copyParams(s, sharedForm.optionsForm)
    s.copyTo(getProperties(sharedForm, usePojoProperties = true))
  }

  override fun applyEditorTo(s: PyAbstractTestConfiguration) {
    AbstractPythonRunConfiguration.copyParams(sharedForm.optionsForm, s)
    s.copyFrom(getProperties(sharedForm, usePojoProperties = true))
  }

  override fun createEditor(): javax.swing.JComponent = sharedForm.panel
}

/**
 * Default target path (run all tests ion project folder)
 */
private val DEFAULT_PATH = ""

/**
 * Target depends on target type. It could be path to file/folder or python target
 */
data class ConfigurationTarget(@ConfigField override var target: String,
                               @ConfigField override var targetType: PyRunTargetVariant) : TargetWithVariant {
  fun copyTo(dst: ConfigurationTarget) {
    // TODO:  do we have such method it in Kotlin?
    dst.target = target
    dst.targetType = targetType
  }

  /**
   * Validates configuration and throws exception if target is invalid
   */
  fun checkValid() {
    if (targetType != PyRunTargetVariant.CUSTOM && target.isEmpty()) {
      throw RuntimeConfigurationWarning("Target not provided")
    }
    if (targetType == PyRunTargetVariant.PYTHON && !isWellFormed()) {
      throw RuntimeConfigurationError("Provide a qualified name of function, class or a module")
    }
  }

  fun asPsiElement(configuration: PyAbstractTestConfiguration): PsiElement? =
    asPsiElement(configuration, configuration.getWorkingDirectoryAsVirtual())

  fun generateArgumentsLine(configuration: PyAbstractTestConfiguration): List<String> =
    when (targetType) {
      PyRunTargetVariant.CUSTOM -> emptyList()
      PyRunTargetVariant.PYTHON -> getArgumentsForPythonTarget(configuration)
      PyRunTargetVariant.PATH -> listOf("--path", target.trim())
    }

  private fun getArgumentsForPythonTarget(configuration: PyAbstractTestConfiguration): List<String> {
    val element = asPsiElement(configuration) ?: throw ExecutionException(
      "Can't resolve $target. Try to remove configuration and generate it again")

    if (element is PsiDirectory) {
      // Directory is special case: we can't run it as package for now, so we run it as path
      return listOf("--path", element.virtualFile.path)
    }

    val context = TypeEvalContext.userInitiated(configuration.project, null)
    val qNameResolveContext = QNameResolveContext(
      contextAnchor = ModuleBasedContextAnchor(configuration.module!!),
      evalContext = context,
      folderToStart = LocalFileSystem.getInstance().findFileByPath(configuration.workingDirectorySafe),
      allowInaccurateResult = true
    )
    val qualifiedNameParts = QualifiedName.fromDottedString(target.trim()).tryResolveAndSplit(qNameResolveContext)
                             ?: throw ExecutionException("Can't find file where $target declared. " +
                                                         "Make sure it is in project root")

    // We can't provide element qname here: it may point to parent class in case of inherited functions,
    // so we make fix file part, but obey element(symbol) part of qname

    if (!configuration.shouldSeparateTargetPath()) {
      // Here generate qname instead of file/path::element_name

      // Try to set path relative to work dir (better than path from closest root)
      // If we can resolve element by this path relative to working directory then use it
      val qNameInsideOfDirectory = qualifiedNameParts.getElementNamePrependingFile()
      val elementAndName = qNameInsideOfDirectory.getElementAndResolvableName(qNameResolveContext.copy(allowInaccurateResult = false))
      if (elementAndName != null) {
        // qNameInsideOfDirectory may contain redundant elements like subtests so we use name that was really resolved
        // element.qname can't be used because inherited test resolves to parent
        return listOf("--target", elementAndName.name.toString())
      }
      // Use "full" (path from closest root) otherwise
      val name = (element.containingFile as? PyFile)?.getQName()?.append(qualifiedNameParts.elementName) ?: throw ExecutionException(
        "Can't get importable name for ${element.containingFile}. Is it a python file in project?")

      return listOf("--target", name.toString())
    }
    else {

      // Here generate file/path::element_name
      val pyTarget = qualifiedNameParts.elementName

      val elementFile = element.containingFile.virtualFile
      val workingDir = elementFile.fileSystem.findFileByPath(configuration.workingDirectorySafe)

      val fileSystemPartOfTarget = (if (workingDir != null) VfsUtil.getRelativePath(elementFile, workingDir)
      else null)
                                   ?: elementFile.path

      if (pyTarget.componentCount == 0) {
        // If python part is empty we are launching file. To prevent junk like "foo.py::" we run it as file instead
        return listOf("--path", fileSystemPartOfTarget)
      }

      return listOf("--target", "$fileSystemPartOfTarget::$pyTarget")

    }
  }

  /**
   * @return directory which target is situated
   */
  fun getElementDirectory(configuration: PyAbstractTestConfiguration): VirtualFile? {
    if (target == DEFAULT_PATH) {
      //This means "current directory", so we do not know where is it
      // getting vitualfile for it may return PyCharm working directory which is not what we want
      return null
    }
    val fileOrDir = asVirtualFile() ?: asPsiElement(configuration)?.containingFile?.virtualFile ?: return null
    return if (fileOrDir.isDirectory) fileOrDir else fileOrDir.parent
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
 */
abstract class PyAbstractTestConfiguration(project: Project,
                                           configurationFactory: ConfigurationFactory,
                                           private val runnerName: String)
  : AbstractPythonTestRunConfiguration<PyAbstractTestConfiguration>(project, configurationFactory), PyRerunAwareConfiguration,
    RefactoringListenerProvider {
  @DelegationProperty
  val target: ConfigurationTarget = ConfigurationTarget(DEFAULT_PATH, PyRunTargetVariant.PATH)
  @ConfigField
  var additionalArguments: String = ""

  val testFrameworkName: String = configurationFactory.name!!


  /**
   * @see [RunnersThatRequireTestCaseClass]
   */
  fun isTestClassRequired(): ThreeState = if (RunnersThatRequireTestCaseClass.contains(runnerName)) {
    ThreeState.YES
  }
  else {
    ThreeState.NO
  }


  @Suppress("LeakingThis") // Legacy adapter is used to support legacy configs. Leak is ok here since everything takes place in one thread
  @DelegationProperty
  val legacyConfigurationAdapter: PyTestLegacyConfigurationAdapter<PyAbstractTestConfiguration> = PyTestLegacyConfigurationAdapter(this)


  /**
   * For real launch use [getWorkingDirectorySafe] instead
   */
  internal fun getWorkingDirectoryAsVirtual(): VirtualFile? {
    if (!workingDirectory.isNullOrEmpty()) {
      return LocalFileSystem.getInstance().findFileByPath(workingDirectory)
    }
    return null
  }

  override fun getWorkingDirectorySafe(): String {
    val dirProvidedByUser = super.getWorkingDirectory()
    if (!dirProvidedByUser.isNullOrEmpty()) {
      return dirProvidedByUser
    }

    return target.getElementDirectory(this)?.path ?: super.getWorkingDirectorySafe()
  }


  override fun getRefactoringElementListener(element: PsiElement?): RefactoringElementListener? {
    if (element == null) return null
    var renamer = CompositeRefactoringElementListener(PyWorkingDirectoryRenamer(getWorkingDirectoryAsVirtual(), this))
    createRefactoringListenerIfPossible(element, target.asPsiElement(this), target.asVirtualFile(), { target.target = it })?.let {
      renamer = renamer.plus(it)
    }
    return renamer
  }


  override fun checkConfiguration() {
    super.checkConfiguration()
    if (!isFrameworkInstalled()) {
      throw RuntimeConfigurationWarning(
        PyBundle.message("runcfg.testing.no.test.framework", testFrameworkName))
    }
    target.checkValid()
  }

  /**
   * Check if framework is available on SDK
   */
  abstract fun isFrameworkInstalled(): Boolean


  override fun isIdTestBased(): Boolean = true

  private fun getPythonTestSpecByLocation(location: Location<*>): List<String> {

    if (location is PyTargetBasedPsiLocation) {
      return location.target.generateArgumentsLine(this)
    }

    if (location !is PsiLocation) {
      return emptyList()
    }
    if (location.psiElement !is PyQualifiedNameOwner) {
      return emptyList()
    }
    val qualifiedName = (location.psiElement as PyQualifiedNameOwner).qualifiedName ?: return emptyList()

    // Resolve name as python qname as last resort
    return ConfigurationTarget(qualifiedName, PyRunTargetVariant.PYTHON).generateArgumentsLine(this)
  }

  override fun getTestSpec(location: Location<*>,
                           failedTest: com.intellij.execution.testframework.AbstractTestProxy): String? {
    val list = getPythonTestSpecByLocation(location)
    if (list.isEmpty()) {
      return null
    }
    else {
      return list.joinToString(" ")
    }
  }

  override fun getTestSpecsForRerun(scope: com.intellij.psi.search.GlobalSearchScope,
                                    locations: MutableList<Pair<Location<*>, AbstractTestProxy>>): List<String> {
    val result = java.util.ArrayList<String>()
    // Set used to remove duplicate targets
    locations.map { it.first }.distinctBy { it.psiElement }.map { getPythonTestSpecByLocation(it) }.filterNotNull().forEach {
      result.addAll(it)
    }
    return result + generateRawArguments(true)
  }

  fun getTestSpec(): List<String> {
    return target.generateArgumentsLine(this) + generateRawArguments()
  }

  /**
   * raw arguments to be added after "--" and passed to runner directly
   */
  private fun generateRawArguments(forRerun: Boolean = false): List<String> {
    val rawArguments = additionalArguments + " " + getCustomRawArgumentsString(forRerun)
    if (rawArguments.isNotBlank()) {
      return listOf("--") + com.intellij.util.execution.ParametersListUtil.parse(rawArguments, false, true)
    }
    return emptyList()
  }

  override fun suggestedName(): String =
    when (target.targetType) {
      PyRunTargetVariant.PATH -> {
        val name = target.asVirtualFile()?.name
        "$testFrameworkName in " + (name ?: target.target)
      }
      PyRunTargetVariant.PYTHON -> {
        "$testFrameworkName for " + target.target
      }
      else -> {
        testFrameworkName
      }
    }


  /**
   * @return configuration-specific arguments
   */
  protected open fun getCustomRawArgumentsString(forRerun: Boolean = false): String = ""

  fun reset() {
    target.target = DEFAULT_PATH
    target.targetType = PyRunTargetVariant.PATH
    additionalArguments = ""
  }

  fun copyFrom(src: Properties) {
    src.copyTo(getConfigFields())
  }

  fun copyTo(dst: Properties) {
    getConfigFields().copyTo(dst)
  }


  override fun writeExternal(element: org.jdom.Element) {
    // Write legacy config to preserve it
    legacyConfigurationAdapter.writeExternal(element)
    // Super is called after to overwrite legacy settings with new one
    super.writeExternal(element)

    val gson = com.google.gson.Gson()

    getConfigFields().properties.forEach {
      val value = it.get()
      if (value != null) {
        // No need to write null since null is default value
        writeField(element, it.prefixedName, gson.toJson(value))
      }
    }
  }

  override fun readExternal(element: org.jdom.Element) {
    super.readExternal(element)

    val gson = com.google.gson.Gson()

    getConfigFields().properties.forEach {
      val fromJson: Any? = gson.fromJson(readField(element, it.prefixedName), it.getType())
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
   * If yes, and element is [PsiElement] then it is [PyRunTargetVariant.PYTHON].
   * If file then [PyRunTargetVariant.PATH]
   */
  fun couldBeTestTarget(element: PsiElement): Boolean {

    // TODO: PythonUnitTestUtil logic is weak. We should give user ability to launch test on symbol since user knows better if folder
    // contains tests etc
    val context = TypeEvalContext.userInitiated(element.project, element.containingFile)
    return isTestElement(element, isTestClassRequired(), context)
  }

  /**
   * There are 2 ways to provide target to runner:
   * * As full qname (package1.module1.Class1.test_foo)
   * * As filesystem path (package1/module1.py::Class1.test_foo) full or relative to working directory
   *
   *  Second approach is prefered if this flag is set. It is generally better because filesystem path does not need __init__.py
   */
  internal open fun shouldSeparateTargetPath(): Boolean = true

  /**
   * @param metaInfo String "metainfo" field provided by test runner
   */
  open fun setMetaInfo(metaInfo: String) {

  }

  /**
   * @return Boolean if metainfo and target produces same configuration
   */
  open fun isSameAsLocation(target: ConfigurationTarget, metainfo: String?): Boolean = target == this.target
}

abstract class PyAbstractTestFactory<out CONF_T : PyAbstractTestConfiguration> : PythonConfigurationFactoryBase(
  PythonTestConfigurationType.getInstance()) {
  override abstract fun createTemplateConfiguration(project: Project): CONF_T
}

/**
 * Only one producer is registered with EP, but it uses factory configured by user to produce different configs
 */
object PyTestsConfigurationProducer : AbstractPythonTestConfigurationProducer<PyAbstractTestConfiguration>(
  PythonTestConfigurationType.getInstance()) {

  override val configurationClass: Class<PyAbstractTestConfiguration> = PyAbstractTestConfiguration::class.java

  override fun createLightConfiguration(context: ConfigurationContext): RunConfiguration? {
    val module = context.module ?: return null
    val project = context.project ?: return null
    val configuration =
      findConfigurationFactoryFromSettings(module).createTemplateConfiguration(project) as? PyAbstractTestConfiguration
      ?: return null
    if (!setupConfigurationFromContext(configuration, context, Ref(context.psiLocation))) return null
    return configuration
  }

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

  // test configuration is always prefered over regular one
  override fun shouldReplace(self: ConfigurationFromContext,
                             other: ConfigurationFromContext): Boolean = other.configuration is PythonRunConfiguration

  override fun isPreferredConfiguration(self: ConfigurationFromContext?,
                                        other: ConfigurationFromContext): Boolean = other.configuration is PythonRunConfiguration

  override fun setupConfigurationFromContext(configuration: PyAbstractTestConfiguration?,
                                             context: ConfigurationContext?,
                                             sourceElement: Ref<PsiElement>?): Boolean {
    if (sourceElement == null || configuration == null) {
      return false
    }
    val element = sourceElement.get() ?: return false

    if (element.containingFile !is PyFile && element !is PsiDirectory) {
      return false
    }

    val location = context?.location
    configuration.module = context?.module
    configuration.isUseModuleSdk = true
    if (location is PyTargetBasedPsiLocation) {
      location.target.copyTo(configuration.target)
      location.metainfo?.let { configuration.setMetaInfo(it)}
    }
    else {
      val targetForConfig = PyTestsConfigurationProducer.getTargetForConfig(configuration,
                                                                            element) ?: return false
      targetForConfig.first.copyTo(configuration.target)
      // Directory may be set in Default configuration. In that case no need to rewrite it.
      if (configuration.workingDirectory.isNullOrEmpty()) {
        configuration.workingDirectory = targetForConfig.second
      }
    }
    configuration.setGeneratedName()
    return true
  }


  /**
   * Inspects file relative imports, finds farthest and returns folder with imported file
   */
  private fun getDirectoryForFileToBeImportedFrom(file: PyFile): PsiDirectory? {
    val maxRelativeLevel = file.fromImports.map { it.relativeLevel }.max() ?: 0
    var elementFolder = file.parent ?: return null
    for (i in 1..maxRelativeLevel) {
      elementFolder = elementFolder.parent ?: return null
    }
    return elementFolder
  }

  /**
   * Creates [ConfigurationTarget] to make  configuration work with provided element.
   * Also reports working dir what should be set to configuration to work correctly
   * @return [target, workingDirectory]
   */
  private fun getTargetForConfig(configuration: PyAbstractTestConfiguration,
                                 baseElement: PsiElement): Pair<ConfigurationTarget, String?>? {


    var element = baseElement
    // Go up until we reach top of the file
    // asking configuration about each element if it is supported or not
    // If element is supported -- set it as configuration target
    do {
      if (configuration.couldBeTestTarget(element)) {
        when (element) {
          is PyQualifiedNameOwner -> { // Function, class, method

            val module = configuration.module ?: return null

            val elementFile = element.containingFile as? PyFile ?: return null
            val workingDirectory = getDirectoryForFileToBeImportedFrom(elementFile) ?: return null
            val context = QNameResolveContext(ModuleBasedContextAnchor(module),
                                              evalContext = TypeEvalContext.userInitiated(configuration.project,
                                                                                          null),
                                              folderToStart = workingDirectory.virtualFile)
            val parts = element.tryResolveAndSplit(context) ?: return null
            val qualifiedName = parts.getElementNamePrependingFile(workingDirectory)
            return Pair(ConfigurationTarget(qualifiedName.toString(), PyRunTargetVariant.PYTHON),
                        workingDirectory.virtualFile.path)
          }
          is PsiFileSystemItem -> {
            val virtualFile = element.virtualFile
            val path = virtualFile

            val workingDirectory = when (element) {
                                     is PyFile -> getDirectoryForFileToBeImportedFrom(element)
                                     is PsiDirectory -> element
                                     else -> return null
                                   }?.virtualFile?.path ?: return null
            return Pair(ConfigurationTarget(path.path, PyRunTargetVariant.PATH), workingDirectory)
          }
        }
      }
      element = element.parent ?: break
    }
    while (element !is PsiDirectory) // if parent is folder, then we are at file level
    return null
  }


  override fun isConfigurationFromContext(configuration: PyAbstractTestConfiguration,
                                          context: ConfigurationContext?): Boolean {

    val location = context?.location
    if (location is PyTargetBasedPsiLocation) {
      // With derived classes several configurations for same element may exist
      return configuration.isSameAsLocation(location.target, location.metainfo)
    }

    val psiElement = context?.psiLocation ?: return false
    val targetForConfig = PyTestsConfigurationProducer.getTargetForConfig(configuration,
                                                                          psiElement) ?: return false

    if (configuration.target != targetForConfig.first) return false


    //Even of both configurations have same targets, it could be that both have same qname which is resolved
    // to different elements due to different working folders.
    // Resolve them and check files
    if (configuration.target.targetType != PyRunTargetVariant.PYTHON) return true

    val targetPsi = targetAsPsiElement(configuration.target.targetType, configuration.target.target, configuration,
                                       configuration.getWorkingDirectoryAsVirtual()) ?: return true
    return targetPsi.containingFile == psiElement.containingFile
  }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
/**
 * Mark run configuration field with it to enable saving, resotring and form iteraction
 */
annotation class ConfigField
