package com.intellij.tools.ide.performanceTesting.commands

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.tools.ide.performanceTesting.commands.dto.*
import java.io.File
import java.lang.reflect.Modifier
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.listDirectoryEntries
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.time.Duration

private const val CMD_PREFIX = '%'

const val WARMUP = "WARMUP"
const val ENABLE_SYSTEM_METRICS = "ENABLE_SYSTEM_METRICS"
val objectMapper = jacksonObjectMapper()

fun <T : CommandChain> T.waitForSmartMode(): T = apply {
  addCommand("${CMD_PREFIX}waitForSmart")
}

fun <T : CommandChain> T.replaceBrowser(): T = apply {
  addCommand("${CMD_PREFIX}replaceBrowser")
}

fun <T : CommandChain> T.logout(): T = apply {
  addCommand("${CMD_PREFIX}logout")
}

fun <T : CommandChain> T.action(id: String): T = apply {
  addCommand("${CMD_PREFIX}action $id")
}

@Suppress("unused")
fun <T : CommandChain> T.waitForDumbMode(maxWaitingTimeInSec: Int): T = apply {
  addCommand("${CMD_PREFIX}waitForDumb ${maxWaitingTimeInSec}")
}

fun <T : CommandChain> T.waitForInitialRefresh(): T = apply {
  addCommand("${CMD_PREFIX}waitForInitialRefresh")
}

fun <T : CommandChain> T.recoveryAction(action: RecoveryActionType): T = apply {
  val possibleArguments = RecoveryActionType.entries.map { it.name }
  require(possibleArguments.contains(action.toString())) {
    "Argument ${action} isn't allowed. Possible values: $possibleArguments"
  }
  addCommand("${CMD_PREFIX}recovery", action.toString())
}

fun <T : CommandChain> T.flushIndexes(): T = apply {
  addCommand("${CMD_PREFIX}flushIndexes")
}

fun <T : CommandChain> T.setupProjectSdk(sdk: SdkObject): T = apply {
  appendRawLine("${CMD_PREFIX}setupSDK \"${sdk.sdkName}\" \"${sdk.sdkType}\" \"${sdk.sdkPath}\"")
}

private fun <T : CommandChain> T.appendRawLine(line: String): T = apply {
  require(!line.contains("\n")) { "Invalid line to include: $line" }
  addCommand(line)
}

fun <T : CommandChain> T.verifyFileEncoding(
  relativePath: String,
  expectedCharsetName: String,
): T = apply {
  addCommand("${CMD_PREFIX}assertEncodingFileCommand", relativePath, expectedCharsetName)
}

fun <T : CommandChain> T.openFile(
  relativePath: String,
  timeoutInSeconds: Long = 0,
  suppressErrors: Boolean = false,
  warmup: Boolean = false,
  disableCodeAnalysis: Boolean = false,
  useWaitForCodeAnalysisCode: Boolean = false,//todo[lene] temporal revert till 16.08.2024, because new analysis breaks reporting span to IJ-Perf
): T = apply {
  val command = mutableListOf("${CMD_PREFIX}openFile", "-file ${relativePath.replace(" ", "SPACE_SYMBOL")}")
  if (timeoutInSeconds != 0L) {
    command.add("-timeout $timeoutInSeconds")
  }
  if (suppressErrors) {
    command.add("-suppressErrors")
  }
  if (disableCodeAnalysis) {
    command.add("-dsa")
  }
  if (warmup) {
    command.add(WARMUP)
  }
  if (useWaitForCodeAnalysisCode) {
    command.add("-unwfca")
  }

  addCommand(*command.toTypedArray())
}

fun <T : CommandChain> T.openRandomFile(extension: String): T = apply {
  addCommand("${CMD_PREFIX}openRandomFile", extension)
}

fun <T : CommandChain> T.openProject(projectPath: Path, openInNewWindow: Boolean = true, detectProjectLeak: Boolean = false): T = apply {
  if (detectProjectLeak && openInNewWindow) throw IllegalArgumentException("To analyze the project leak, we need to close the project")
  addCommand("${CMD_PREFIX}openProject", projectPath.toString(), (!openInNewWindow).toString(), detectProjectLeak.toString())
}

fun <T : CommandChain> T.reopenProject(): T = apply {
  addCommand("${CMD_PREFIX}openProject")
}

fun <T : CommandChain> T.closeProject(): T = apply {
  addCommand("${CMD_PREFIX}closeProject")
}

fun <T : CommandChain> T.storeIndices(): T = apply {
  addCommand("${CMD_PREFIX}storeIndices")
}

fun <T : CommandChain> T.compareIndices(): T = apply {
  addCommand("${CMD_PREFIX}compareIndices")
}

fun <T : CommandChain> T.goto(offset: Int): T = apply {
  addCommand("${CMD_PREFIX}goto", offset.toString())
}

fun <T : CommandChain> T.goto(line: Int, column: Int): T = apply {
  addCommand("${CMD_PREFIX}goto", line.toString(), column.toString())
}

fun <T : CommandChain> T.goto(goto: Pair<Int, Int>): T = apply {
  goto(goto.first, goto.second)
}

fun <T : CommandChain> T.gotoLine(line: Int): T = apply {
  addCommand("${CMD_PREFIX}goto", line.toString(), "0")
}

@Suppress("unused")
fun <T : CommandChain> T.gotoNextPsiElement(vararg name: String): T = apply {
  addCommand("${CMD_PREFIX}goToNextPsiElement", *name)
}

fun <T : CommandChain> T.gotoNextPsiElementIfExist(vararg name: String): T = apply {
  addCommand("${CMD_PREFIX}goToNextPsiElement", *name, "SUPPRESS_ERROR_IF_NOT_FOUND")
}

fun <T : CommandChain> T.gotoNamedPsiElement(name: String, position: Position = Position.INTO): T = apply {
  addCommand("${CMD_PREFIX}goToNamedPsiElement", position.name, name)
}

@Suppress("unused")
fun <T : CommandChain> T.gotoNamedPsiElementIfExist(name: String, position: Position = Position.INTO): T = apply {
  addCommand("${CMD_PREFIX}goToNamedPsiElement", name, position.name, "SUPPRESS_ERROR_IF_NOT_FOUND")
}

fun <T : CommandChain> T.findUsages(expectedElementName: String = "", scope: String = "Project Files", warmup: Boolean = false): T = apply {
  navigateAndFindUsages(expectedElementName, "", scope, warmup = warmup)
}

fun <T : CommandChain> T.navigateAndFindUsages(
  expectedElementName: String,
  position: String = "INTO",
  scope: String = "Project Files",
  warmup: Boolean = false,
  runInBackground: Boolean = false,
): T = apply {
  val command = mutableListOf("${CMD_PREFIX}findUsages")
  if (expectedElementName.isNotEmpty()) {
    command.add("-expectedName $expectedElementName")
    if (position.isNotEmpty()) {
      command.add("-position $position")
    }
  }

  if (scope.isNotEmpty()) {
    command.add("-scope $scope")
  }

  if (runInBackground) {
    command.add("-runInBackground")
  }

  if (warmup) {
    command.add("WARMUP")
  }

  addCommandWithSeparator("|", *command.toTypedArray())
}

fun <T : CommandChain> T.inspectCode(): T = apply {
  addCommand("${CMD_PREFIX}inspectCode")
}

fun <T : CommandChain> T.inspectCodeEx(
  scopeName: String = "",
  toolShortName: String = "",
  inspectionTrueFields: List<String> = listOf(),
  inspectionFalseFields: List<String> = listOf(),
  downloadFileUrl: String = "",
  directory: String = "",
  hideResults: Boolean = false,
): T = apply {
  var resultCommand = ""
  if (scopeName.isNotBlank()) {
    resultCommand += " -scopeName $scopeName"
  }
  if (toolShortName.isNotBlank()) {
    resultCommand += " -toolShortName $toolShortName"
  }
  if (inspectionTrueFields.isNotEmpty()) {
    resultCommand += " -inspectionTrueFields"
    inspectionTrueFields.forEach {
      resultCommand += " $it"
    }
  }
  if (inspectionFalseFields.isNotEmpty()) {
    resultCommand += " -inspectionFalseFields"
    inspectionFalseFields.forEach {
      resultCommand += " $it"
    }
  }
  if (downloadFileUrl.isNotBlank()) {
    resultCommand += " -downloadFileUrl $downloadFileUrl"
  }
  if (directory.isNotBlank()) {
    resultCommand += " -directory $directory"
  }
  resultCommand += " -hideResults $hideResults"

  addCommand("${CMD_PREFIX}InspectCodeEx" + resultCommand)
}

fun <T : CommandChain> T.checkOnRedCode(): T = apply {
  addCommand("${CMD_PREFIX}codeAnalysis ${CodeAnalysisType.CHECK_ON_RED_CODE}")
}

fun <T : CommandChain> T.checkWarnings(vararg args: String): T = apply {
  val setArgs = args.toSet()
  val stringBuilder = StringBuilder("")
  setArgs.forEach { stringBuilder.append("$it,") }
  addCommand("${CMD_PREFIX}codeAnalysis ${CodeAnalysisType.WARNINGS_ANALYSIS} $stringBuilder")
}

fun <T : CommandChain> T.project(project: File): T = apply {
  addCommand("%%project ${project.absolutePath}")
}

fun <T : CommandChain> T.exitApp(forceExit: Boolean = true): T = apply {
  takeScreenshot("exitApp")
  addCommand("${CMD_PREFIX}exitApp", forceExit.toString())
}

fun <T : CommandChain> T.exitAppWithTimeout(timeoutInSeconds: Long): T = apply {
  addCommand("${CMD_PREFIX}exitAppWithTimeout", timeoutInSeconds.toString())
}

fun <T : CommandChain> T.startProfile(args: String): T = apply {
  addCommand("${CMD_PREFIX}startProfile $args")
}

fun <T : CommandChain> T.startProfile(profileFileName: String, profilerParams: String): T = apply {
  addCommand("${CMD_PREFIX}startProfile $profileFileName $profilerParams")
}

fun <T : CommandChain> T.stopProfile(profilerParams: String = "jfr"): T = apply {
  addCommand("${CMD_PREFIX}stopProfile $profilerParams")
}

fun <T : CommandChain> T.memoryDump(): T = apply {
  addCommand("${CMD_PREFIX}memoryDump")
}

fun <T : CommandChain> T.conditionalMemoryDump(targetMessageCount: Int, withErrorMessage: Boolean = false): T = apply {
  val ext = if (withErrorMessage) " WITH_ERROR_MESSAGE" else ""
  addCommand("${CMD_PREFIX}conditionalMemoryDumpCommand ${targetMessageCount}${ext}")
}

@Suppress("unused")
fun <T : CommandChain> T.profileIndexing(args: String): T = apply {
  addCommand("%%profileIndexing $args")
}

fun <T : CommandChain> T.corruptIndexes(pathToIndexesDir: Path, additionalDir: String = ""): T = apply {
  if (additionalDir.isEmpty()) {
    addCommand("${CMD_PREFIX}corruptIndex", pathToIndexesDir.toString())
  }
  else {
    addCommand("${CMD_PREFIX}corruptIndex", pathToIndexesDir.toString(), additionalDir)
  }
}

@Suppress("unused")
fun <T : CommandChain> T.corruptIndexPerDir(indexDir: Path): T = apply {
  val dirs = indexDir
    .listDirectoryEntries()
    .filter { it.toFile().isDirectory }
    .filter { it.toFile().name != "stubs" && it.toFile().name != "filetypes" }
    .toList()
  println("Corrupting dirs count: ${dirs.size} list: $dirs")
  dirs.forEach {
    corruptIndexes(indexDir, it.toFile().name)
    flushIndexes()
    checkOnRedCode()
  }
}

@Suppress("unused")
fun <T : CommandChain> T.corruptIndexPerFile(indexDir: Path): T = apply {
  val filesInDir = indexDir
    .toFile()
    .walkTopDown()
    .filter { it.isFile }
    .toList()
  println("Corrupting ${filesInDir.size}")
  filesInDir.forEach {
    corruptIndexes(it.toPath())
    flushIndexes()
    checkOnRedCode()
  }
}

fun <T : CommandChain> T.dumpProjectFiles(): T = apply {
  addCommand("${CMD_PREFIX}dumpProjectFiles")
}

fun <T : CommandChain> T.compareProjectFiles(previousDir: String): T = apply {
  addCommand("${CMD_PREFIX}compareProjectFiles", previousDir)
}

fun <T : CommandChain> T.cleanCaches(): T = apply {
  addCommand("${CMD_PREFIX}cleanCaches")
}

fun <T : CommandChain> T.doComplete(completionType: CompletionType = CompletionType.BASIC): T = apply {
  addCommand("${CMD_PREFIX}doComplete", completionType.name)
}

fun <T : CommandChain> T.doCompleteWarmup(completionType: CompletionType = CompletionType.BASIC): T = apply {
  addCommand("${CMD_PREFIX}doComplete", completionType.name, WARMUP)
}

fun <T : CommandChain> T.doCompleteInEvaluateExpression(completionType: CompletionType = CompletionType.BASIC): T = apply {
  addCommand("${CMD_PREFIX}doCompleteInEvaluateExpression", completionType.name)
}

fun <T : CommandChain> T.doCompleteInEvaluateExpressionWarmup(completionType: CompletionType = CompletionType.BASIC): T = apply {
  addCommand("${CMD_PREFIX}doCompleteInEvaluateExpression", completionType.name, WARMUP)
}

fun <T : CommandChain> T.doComplete(times: Int): T = apply {
  repeat(times) {
    doComplete()
    pressKey(Keys.ESCAPE)
    cleanCaches()
  }
}

fun <T : CommandChain> T.doHighlightingWarmup(): T = apply {
  addCommand("${CMD_PREFIX}doHighlight", WARMUP)
}

fun <T : CommandChain> T.doHighlighting(): T = apply {
  addCommand("${CMD_PREFIX}doHighlight")
}

fun <T : CommandChain> T.openProjectView(): T = apply {
  addCommand("${CMD_PREFIX}openProjectView")
}

fun <T : CommandChain> T.getLibraryPathByName(name: String, path: Path): T = apply {
  addCommand("${CMD_PREFIX}getLibraryPathByName $name,$path")
}

fun <T : CommandChain> T.convertJavaToKotlin(moduleName: String, filePath: String): T = apply {
  addCommand("${CMD_PREFIX}convertJavaToKotlin $moduleName $filePath")
}

/**
 * @see [com.jetbrains.performancePlugin.commands.IdeEditorKeyCommand]
 */
fun <T : CommandChain> T.pressKey(key: Keys): T = apply {
  addCommand("${CMD_PREFIX}pressKey", key.name)
}

fun <T : CommandChain> T.pressKey(vararg key: Keys): T = apply {
  key.forEach { addCommand("${CMD_PREFIX}pressKey", it.name) }
}

fun <T : CommandChain> T.pressKey(key: Keys, times: Int): T = apply {
  repeat((1..times).count()) { addCommand("${CMD_PREFIX}pressKey", key.name) }
}

/**
 * @see [com.jetbrains.performancePlugin.commands.DelayTypeCommand]
 */
fun <T : CommandChain> T.delayType(
  delayMs: Int,
  text: String,
  calculateAnalyzesTime: Boolean = false,
  disableWriteProtection: Boolean = false,
): T = apply {
  addCommand("${CMD_PREFIX}delayType", "$delayMs|$text|$calculateAnalyzesTime|$disableWriteProtection")
}

fun <T : CommandChain> T.doLocalInspection(spanTag: String? = null): T = apply {
  val spanTagLine = spanTag?.let { " spanTag $spanTag" } ?: ""
  addCommand("${CMD_PREFIX}doLocalInspection" + spanTagLine)
}

fun <T : CommandChain> T.runSingleInspection(inspectionName: String, scope: String): T = apply {
  addCommand("${CMD_PREFIX}runSingleInspection", inspectionName, scope)
}

fun <T : CommandChain> T.doLocalInspectionWarmup(): T = apply {
  addCommand("${CMD_PREFIX}doLocalInspection", WARMUP)
}

fun <T : CommandChain> T.altEnter(intention: String, invoke: Boolean): T = apply {
  addCommand("${CMD_PREFIX}altEnter", "$intention|$invoke")
}

fun <T : CommandChain> T.callAltEnter(times: Int, intention: String = "", invoke: Boolean = true): T = apply {
  repeat(times) {
    altEnter(intention, invoke)
  }
}

fun <T : CommandChain> T.createAllServicesAndExtensions(): T = apply {
  addCommand("${CMD_PREFIX}CreateAllServicesAndExtensions")
}

fun <T : CommandChain> T.runConfiguration(
  configurationName: String,
  mode: String = "TILL_TERMINATED",
  failureExpected: Boolean = false,
  debug: Boolean = false,
): T = apply {
  val command = mutableListOf("${CMD_PREFIX}runConfiguration")
  command.add("-configurationName=$configurationName")
  command.add("-mode=$mode")
  if (failureExpected) {
    command.add("-failureExpected")
  }
  if (debug) {
    command.add("-debug")
  }
  addCommandWithSeparator("|", *command.toTypedArray())
}

fun <T : CommandChain> T.openFileWithTerminate(relativePath: String, terminateIdeInSeconds: Long): T = apply {
  addCommand("${CMD_PREFIX}openFileWithTerminate $relativePath $terminateIdeInSeconds")
}

fun <T : CommandChain> T.startPowerSave(): T = apply {
  addCommand("${CMD_PREFIX}startPowerSave")
}

fun <T : CommandChain> T.stopPowerSave(): T = apply {
  addCommand("${CMD_PREFIX}stopPowerSave")
}

fun <T : CommandChain> T.searchEverywhere(
  tab: String = "all",
  textToInsert: String = "",
  textToType: String = "",
  close: Boolean = false,
  selectFirst: Boolean = false,
  warmup: Boolean = false,
): T = apply {
  val closeOnOpenArgument = when {
    close -> "-close"
    else -> ""
  }
  val selectFirstArgument = when {
    selectFirst -> "-selectFirst"
    else -> ""
  }
  val argumentForTyping = when {
    textToType.isNotEmpty() -> "-type $textToType"
    else -> ""
  }
  val warmupText = if (warmup) "|WARMUP" else ""
  if (selectFirstArgument.isNotEmpty() && closeOnOpenArgument.isNotEmpty()) {
    throw Exception("selectFirst=true argument will be ignored since close=true and SE will be closed first")
  }
  addCommand("${CMD_PREFIX}searchEverywhere", "-tab $tab $closeOnOpenArgument $selectFirstArgument $argumentForTyping|$textToInsert$warmupText")
}

fun <T : CommandChain> T.selectFileInProjectView(relativePath: String): T = apply {
  addCommand("${CMD_PREFIX}selectFileInProjectView", relativePath)
}

fun <T : CommandChain> T.expandProjectMenu(): T = apply {
  addCommand("${CMD_PREFIX}expandProjectMenu")
}

fun <T : CommandChain> T.expandMainMenu(): T = apply {
  addCommand("${CMD_PREFIX}expandMainMenu")
}

fun <T : CommandChain> T.closeAllTabs(): T = apply {
  addCommand("${CMD_PREFIX}closeAllTabs")
}

fun <T : CommandChain> T.expandEditorMenu(): T = apply {
  addCommand("${CMD_PREFIX}expandEditorMenu")
}

fun <T : CommandChain> T.takeScreenshot(path: String): T = apply {
  addCommand("${CMD_PREFIX}takeScreenshot", path)
}

fun <T : CommandChain> T.takeThreadDump(): T = apply {
  addCommand("${CMD_PREFIX}takeThreadDump")
}

fun <T : CommandChain> T.recordRegisteredCounterGroups(): T = apply {
  addCommand("${CMD_PREFIX}recordRegisteredCounterGroups")
}

fun <T : CommandChain> T.recordStateCollectors(): T = apply {
  addCommand("${CMD_PREFIX}recordStateCollectors")
}

@Suppress("unused")
fun <T : CommandChain> T.flushFusEvents(): T = apply {
  addCommand("${CMD_PREFIX}flushFusEvents")
}

fun <T : CommandChain> T.reloadFiles(filePaths: List<String> = listOf()): T = apply {
  addCommand("${CMD_PREFIX}reloadFiles ${filePaths.joinToString(" ")}")
}

fun <T : CommandChain> T.addFile(path: String, fileName: String): T = apply {
  addCommand("${CMD_PREFIX}addFile ${path}, ${fileName}")
}

fun <T : CommandChain> T.renameFile(path: String, oldFileName: String, newFileName: String): T = apply {
  addCommand("${CMD_PREFIX}renameFile ${path}, ${oldFileName}, ${newFileName}")
}

fun <T : CommandChain> T.requestHeavyScanningOnNextStart(): T = apply {
  addCommand("${CMD_PREFIX}requestHeavyScanningOnNextStart")
}

fun <T : CommandChain> T.call(method: KFunction<String?>, vararg args: String): T = apply {
  val javaMethod = method.javaMethod ?: error("Failed to resolve Java Method from the declaration")
  require(Modifier.isStatic(javaMethod.modifiers)) { "Method $method must be static" }
  addCommand("${CMD_PREFIX}importCall ${javaMethod.declaringClass.name}")
  addCommand("""${CMD_PREFIX}call ${javaMethod.name}(${args.joinToString(", ")})""")
}

fun <T : CommandChain> T.deleteFile(path: String, fileName: String): T = apply {
  addCommand("${CMD_PREFIX}deleteFile ${path}, ${fileName}")
}

fun <T : CommandChain> T.delay(delayMs: Int): T = apply {
  addCommand("${CMD_PREFIX}delay ${delayMs}")
}

fun <T : CommandChain> T.delay(delay: Duration): T = apply {
  addCommand("${CMD_PREFIX}delay ${delay.inWholeMilliseconds}")
}

fun <T : CommandChain> T.withSystemMetrics(chain: CommandChain): T = apply {
  if (chain == this) throw IllegalStateException("Current command chain provided")
  for (command in chain) {
    addCommand(command.storeToString(), ENABLE_SYSTEM_METRICS)
  }
}

fun <T : CommandChain> T.selectText(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int): T = apply {
  addCommand("${CMD_PREFIX}selectText", startLine.toString(), startColumn.toString(), endLine.toString(), endColumn.toString())
}

fun <T : CommandChain> T.showFileStructureDialog(): T = apply {
  addCommand("${CMD_PREFIX}showFileStructureDialog")
}

fun <T : CommandChain> T.importMavenProject(): T = apply {
  addCommand("${CMD_PREFIX}importMavenProject")
}

fun <T : CommandChain> T.updateMavenFolders(isErrorExpected: Boolean = false): T = apply {
  addCommand("${CMD_PREFIX}updateMavenFolders $isErrorExpected")
}

fun <T : CommandChain> T.mavenIndexUpdate(repoUrl: String = ""): T = apply {
  addCommand("${CMD_PREFIX}mavenIndexUpdate $repoUrl")
}

fun <T : CommandChain> T.checkIfMavenIndexesHaveArtefact(info: MavenArchetypeInfo): T = apply {
  val options = objectMapper.writeValueAsString(info)
  addCommand("${CMD_PREFIX}checkIfMavenIndexesHaveArtefact $options")
}

enum class AssertModuleJdkVersionMode {
  CONTAINS,
  EQUALS
}

fun <T : CommandChain> T.assertModuleJdkVersion(
  moduleName: String,
  jdkVersion: String,
  mode: AssertModuleJdkVersionMode = AssertModuleJdkVersionMode.CONTAINS,
): T {
  val command = mutableListOf("${CMD_PREFIX}assertModuleJdkVersionCommand")
  command.add("-moduleName=$moduleName")
  command.add("-jdkVersion=$jdkVersion")
  command.add("-mode=$mode")
  addCommandWithSeparator("|", *command.toTypedArray())
  return this
}

fun <T : CommandChain> T.setModuleJdk(moduleName: String, jdk: SdkObject): T {
  val command = mutableListOf("${CMD_PREFIX}setModuleJdk")
  command.add("-moduleName=$moduleName")
  command.add("-jdkName=${jdk.sdkName}")
  command.add("-jdkType=${jdk.sdkType}")
  command.add("-jdkPath=${jdk.sdkPath}")
  addCommandWithSeparator("|", *command.toTypedArray())
  return this
}

fun <T : CommandChain> T.addModuleContentRoot(moduleName: String, contentRootPath: String): T = apply {
  addCommand("${CMD_PREFIX}addContentRootToModule $moduleName,$contentRootPath")
}

fun <T : CommandChain> T.toggleMavenProfiles(profileIds: Set<String>, enable: Boolean = true): T = apply {
  addCommand("${CMD_PREFIX}toggleMavenProfiles ${profileIds.joinToString(",")} $enable")
}

fun <T : CommandChain> T.linkMavenProject(projectPath: Path): T = apply {
  addCommand("${CMD_PREFIX}linkMavenProject ${projectPath}")
}

fun <T : CommandChain> T.linkGradleProject(projectPath: Path): T = apply {
  addCommand("${CMD_PREFIX}linkGradleProject ${projectPath}")
}

fun <T : CommandChain> T.analyzeDependencies(moduleName: String, providerId: BuildType): T = apply {
  addCommand("${CMD_PREFIX}analyzeDependencies $moduleName $providerId")
}

fun <T : CommandChain> T.refreshMavenProject(failureExpectedPattern: String = ""): T = apply {
  addCommand("${CMD_PREFIX}refreshMavenProject $failureExpectedPattern")
}

fun <T : CommandChain> T.refreshGradleProject(): T = apply {
  addCommand("${CMD_PREFIX}refreshGradleProject")
}

fun <T : CommandChain> T.setGradleDelegatedBuildCommand(
  delegatedBuild: Boolean = true,
  gradleTestRunner: GradleTestRunner = GradleTestRunner.GRADLE,
): T = apply {
  addCommand("${CMD_PREFIX}setGradleDelegatedBuildCommand $delegatedBuild $gradleTestRunner")
}

fun <T : CommandChain> T.setMavenDelegatedBuild(delegatedBuild: Boolean = false): T = apply {
  addCommand("${CMD_PREFIX}setMavenDelegatedBuild $delegatedBuild")
}

fun <T : CommandChain> T.unlinkGradleProject(projectPath: Path): T = apply {
  addCommand("${CMD_PREFIX}unlinkGradleProject ${projectPath}")
}

fun <T : CommandChain> T.unlinkMavenProject(projectPath: Path): T = apply {
  addCommand("${CMD_PREFIX}unlinkMavenProject ${projectPath}")
}

fun <T : CommandChain> T.downloadGradleSources(): T = apply {
  addCommand("${CMD_PREFIX}downloadGradleSources")
}

fun <T : CommandChain> T.downloadMavenArtifacts(sources: Boolean = true, docs: Boolean = true): T = apply {
  addCommand("${CMD_PREFIX}downloadMavenArtifacts $sources $docs")
}

fun <T : CommandChain> T.createMavenProject(newMavenProjectDto: NewMavenProjectDto): T = apply {
  val options = objectMapper.writeValueAsString(newMavenProjectDto)
  addCommand("${CMD_PREFIX}createMavenProject $options")
}

fun <T : CommandChain> T.renameModule(oldName: String, newName: String): T = apply {
  addCommand("${CMD_PREFIX}renameModule $oldName $newName")
}

fun <T : CommandChain> T.createGradleProject(newGradleProjectDto: NewGradleProjectDto): T = apply {
  val options = objectMapper.writeValueAsString(newGradleProjectDto)
  addCommand("${CMD_PREFIX}createGradleProject $options")
}

fun <T : CommandChain> T.createSpringProject(newMavenProjectDto: NewSpringProjectDto): T = apply {
  val options = objectMapper.writeValueAsString(newMavenProjectDto)
  addCommand("${CMD_PREFIX}createSpringProject $options")
}

fun <T : CommandChain> T.updateMavenGoal(settings: MavenGoalConfigurationDto): T = apply {
  val options = objectMapper.writeValueAsString(settings)
  addCommand("${CMD_PREFIX}updateMavenGoal $options")
}

fun <T : CommandChain> T.validateMavenGoal(settings: MavenGoalConfigurationDto): T = apply {
  val options = objectMapper.writeValueAsString(settings)
  addCommand("${CMD_PREFIX}validateMavenGoal $options")
}

fun <T : CommandChain> T.executeMavenGoals(settings: MavenGoalConfigurationDto): T {
  val options = objectMapper.writeValueAsString(settings)
  addCommand("${CMD_PREFIX}executeMavenGoals $options")
  return this
}

fun <T : CommandChain> T.inlineRename(to: String): T = apply {
  startInlineRename()
  delayType(150, to)
  finishInlineRename()
}

fun <T : CommandChain> T.finishInlineRename(): T = apply {
  addCommand("${CMD_PREFIX}finishInlineRename")
}

fun <T : CommandChain> T.startInlineRename(): T = apply {
  addCommand("${CMD_PREFIX}startInlineRename")
}

fun <T : CommandChain> T.setRegistry(registry: String, value: Boolean): T = apply {
  addCommand("${CMD_PREFIX}set $registry=$value")
}

fun <T : CommandChain> T.setRegistry(registry: String, value: String): T = apply {
  addCommand("${CMD_PREFIX}set $registry=$value")
}

fun <T : CommandChain> T.validateGradleMatrixCompatibility(): T = apply {
  addCommand("${CMD_PREFIX}validateGradleMatrixCompatibility")
}

fun <T : CommandChain> T.collectNameSuggestionContext(file: String, offset: Int): T = apply {
  addCommand("${CMD_PREFIX}collectNameSuggestionContext $file $offset")
}

@Suppress("unused")
fun <T : CommandChain> T.waitForLlmNameSuggestions(file: String, offset: Int): T = apply {
  addCommand("${CMD_PREFIX}waitForLlmNameSuggestions $file $offset")
}

fun <T : CommandChain> T.assertOpenedFileInRoot(path: String): T = apply {
  addCommand("${CMD_PREFIX}assertOpenedFileInRoot $path")
}

fun <T : CommandChain> T.importGradleProject(): T = apply {
  addCommand("${CMD_PREFIX}importGradleProject")
}

fun <T : CommandChain> T.awaitCompleteProjectConfiguration(): T = apply {
  addCommand("${CMD_PREFIX}awaitCompleteProjectConfiguration")
}

fun <T : CommandChain> T.executeGradleTask(taskInfo: GradleTaskInfoDto): T {
  val options = objectMapper.writeValueAsString(taskInfo)
  addCommand("${CMD_PREFIX}executeGradleTask $options")
  return this
}

fun <T : CommandChain> T.setBuildToolsAutoReloadType(type: BuildToolsAutoReloadType): T = apply {
  addCommand("${CMD_PREFIX}setBuildToolsAutoReloadType $type")
}

fun <T : CommandChain> T.projectNotificationAwareShouldBeVisible(shouldBeVisible: Boolean): T = apply {
  addCommand("${CMD_PREFIX}projectNotificationAwareShouldBeVisible $shouldBeVisible")
}

fun <T : CommandChain> T.setGradleJdk(jdk: SdkObject): T = apply {
  addCommand("${CMD_PREFIX}setGradleJdk ${jdk.sdkName}|${jdk.sdkType}|${jdk.sdkPath}")
}

fun <T : CommandChain> T.showEvaluateExpression(
  expression: String = "",
  performEvaluateCount: Int = 0,
  warmup: Boolean = false,
): T = apply {
  val command = mutableListOf("${CMD_PREFIX}showEvaluateExpression")
  if (expression.isNotEmpty()) {
    command.add("-expression $expression")
  }
  command.add("-performEvaluateCount $performEvaluateCount")
  if (warmup) {
    command.add("WARMUP")
  }
  addCommandWithSeparator("|", *command.toTypedArray())
}

fun <T : CommandChain> T.executeEditorAction(action: String): T = apply {
  addCommand("${CMD_PREFIX}executeEditorAction $action")
}

fun <T : CommandChain> T.moveFiles(moveFileData: MoveFilesData): T = apply {
  val jsonData = objectMapper.writeValueAsString(moveFileData)
  addCommand("${CMD_PREFIX}moveFiles $jsonData")
}

fun <T : CommandChain> T.performGC(): T = apply {
  addCommand("${CMD_PREFIX}performGC")
}

fun <T : CommandChain> T.copy(): T = apply {
  executeEditorAction("\$Copy")
}

fun <T : CommandChain> T.past(): T = apply {
  executeEditorAction("\$Paste")
}

@Suppress("unused")
fun <T : CommandChain> T.cut(): T = apply {
  executeEditorAction("\$Cut")
}

@Suppress("unused")
fun <T : CommandChain> T.undo(): T = apply {
  executeEditorAction("\$Undo")
}

fun <T : CommandChain> T.selectAll(): T = apply {
  executeEditorAction("\$SelectAll")
}

fun <T : CommandChain> T.checkoutBranch(branch: String, newBranchName: String = branch): T = apply {
  addCommand("${CMD_PREFIX}gitCheckout $branch $newBranchName")
}

fun <T : CommandChain> T.showFileHistory(): T = apply {
  addCommand("${CMD_PREFIX}showFileHistory")
}

fun <T : CommandChain> T.filterVcsLogTab(params: Map<String, String>): T = apply {
  val cmdParams = params.map { "-${it.key} '${it.value}'" }.joinToString(" ")
  addCommand("${CMD_PREFIX}filterVcsLogTab $cmdParams")
}

fun <T : CommandChain> T.showBranchWidget(): T = apply {
  addCommand("${CMD_PREFIX}gitShowBranchWidget")
}

fun <T : CommandChain> T.showFileAnnotations(): T = apply {
  addCommand("${CMD_PREFIX}showFileAnnotation")
}

fun <T : CommandChain> T.chooseCompletionCommand(completionName: String): T = apply {
  addCommand("${CMD_PREFIX}chooseCompletionCommand ${completionName}")
}

fun <T : CommandChain> T.assertCompletionCommand(): T = apply {
  addCommand("${CMD_PREFIX}assertCompletionCommand EXIST")
}

@Suppress("unused")
fun <T : CommandChain> T.assertCompletionCommandContains(completionNames: List<String>): T = apply {
  addCommand("${CMD_PREFIX}assertCompletionCommand CONTAINS ${completionNames.joinToString(" ")}")
}

@Suppress("unused")
fun <T : CommandChain> T.assertCompletionCommandCount(count: Int): T = apply {
  addCommand("${CMD_PREFIX}assertCompletionCommand COUNT ${count}")
}

fun <T : CommandChain> T.goToDeclaration(expectedOpenedFile: String? = null, spanTag: String? = null): T = apply {
  val action = StringBuilder("GotoDeclaration")
  if (expectedOpenedFile != null) action.append(" expectedOpenedFile $expectedOpenedFile")
  if (spanTag != null) action.append(" spanTag $spanTag")
  executeEditorAction(action.toString())
}

fun <T : CommandChain> T.goToImplementation(): T = apply {
  val action = StringBuilder("GotoImplementation")
  executeEditorAction(action.toString())
}


fun <T : CommandChain> T.collectAllFiles(extension: String, fromSources: Boolean = true): T = apply {
  addCommand("${CMD_PREFIX}collectAllFiles $extension $fromSources")
}

fun <T : CommandChain> T.storeHighlightingResults(fileName: String): T = apply {
  addCommand("${CMD_PREFIX}storeHighlightingResults $fileName")
}

fun <T : CommandChain> T.recompileFiles(relativeFilePaths: List<String>): T = apply {
  addCommand("${CMD_PREFIX}buildProject RECOMPILE_FILES ${relativeFilePaths.joinToString(" ")}".trim())
}

fun <T : CommandChain> T.build(moduleNames: List<String> = listOf()): T = apply {
  addCommand("${CMD_PREFIX}buildProject BUILD ${moduleNames.joinToString(" ")}".trim())
}

fun <T : CommandChain> T.rebuild(moduleNames: List<String> = listOf()): T = apply {
  addCommand("${CMD_PREFIX}buildProject REBUILD ${moduleNames.joinToString(" ")}".trim())
}

fun <T : CommandChain> T.syncJpsLibraries(): T = apply {
  addCommand("${CMD_PREFIX}syncJpsLibraries")
}

//kotlin
fun <T : CommandChain> T.clearSourceCaches(): T = apply {
  addCommand("${CMD_PREFIX}clearSourceCaches")
}

fun <T : CommandChain> T.clearLibraryCaches(): T = apply {
  addCommand("${CMD_PREFIX}clearLibraryCaches")
}

fun <T : CommandChain> T.convertJavaToKotlinByDefault(value: Boolean): T = apply {
  addCommand("${CMD_PREFIX}changeKotlinEditorOptions donTShowConversionDialog ${value}")
}

fun <T : CommandChain> T.assertOpenedKotlinFileInRoot(path: String): T = apply {
  addCommand("${CMD_PREFIX}assertOpenedKotlinFileInRoot ${path}")
}

fun <T : CommandChain> T.enableKotlinDaemonLog(): T = apply {
  addCommand("${CMD_PREFIX}enableKotlinDaemonLog")
}

fun <T : CommandChain> T.addKotlinCompilerOptions(vararg options: String): T = apply {
  addCommand("${CMD_PREFIX}addKotlinCompilerOptions ${options.joinToString(" ")}")
}

fun <T : CommandChain> T.assertFindUsagesCount(count: Int): T = apply {
  addCommand("${CMD_PREFIX}assertFindUsagesCommand ${count}")
}

fun <T : CommandChain> T.setBreakpoint(line: Int, relativePath: String? = null, isLambdaBreakpoint: Boolean = false): T = apply {
  val ext = when {
    relativePath != null -> ", ${relativePath}"
    isLambdaBreakpoint -> ", lambda-type"
    else -> ""
  }
  addCommand("${CMD_PREFIX}setBreakpoint ${line}${ext}")
}

fun <T : CommandChain> T.removeAllBreakpoints(): T = apply {
  addCommand("${CMD_PREFIX}removeBreakpoint all")
}

fun <T : CommandChain> T.debugRunConfiguration(runConfigurationName: String, maxWaitingTimeInSec: Int? = null): T = apply {
  val ext = if (maxWaitingTimeInSec != null) ",${maxWaitingTimeInSec}" else ""
  addCommand("${CMD_PREFIX}debugRunConfiguration ${runConfigurationName}${ext}")
}

fun <T : CommandChain> T.debugStep(debugStepTypes: DebugStepTypes): T = apply {
  addCommand("${CMD_PREFIX}debugStep ${debugStepTypes.name}")
}

fun <T : CommandChain> T.stopDebugProcess(): T = apply {
  addCommand("${CMD_PREFIX}stopDebugProcess")
}

fun <T : CommandChain> T.waitForCodeAnalysisFinished(): T = apply {
  addCommand("${CMD_PREFIX}waitForFinishedCodeAnalysis")
}

@Suppress("unused")
fun <T : CommandChain> T.checkChatBotResponse(textToCheck: String): T = apply {
  addCommand("${CMD_PREFIX}checkResponseContains ${textToCheck}")
}

fun <T : CommandChain> T.authenticateInGrazie(token: String): T = apply {
  addCommand("${CMD_PREFIX}authenticateInGrazie ${token}")
}

fun <T : CommandChain> T.waitFullLineModelLoaded(language: String): T = apply {
  addCommand("${CMD_PREFIX}waitFullLineModelLoaded ${language}")
}

fun <T : CommandChain> T.waitKotlinFullLineModelLoaded(): T = apply {
  waitFullLineModelLoaded("kotlin")
}

fun <T : CommandChain> T.createJavaFile(fileName: String, filePath: String, fileType: String): T = apply {
  addCommand("${CMD_PREFIX}createJavaFile $fileName,$filePath,$fileType")
}

fun <T : CommandChain> T.createKotlinFile(fileName: String, filePath: String, fileType: String): T = apply {
  addCommand("${CMD_PREFIX}createKotlinFile $fileName,$filePath,$fileType")
}

enum class EnableSettingSyncOptions {
  GET, PUSH, NONE
}

fun <T : CommandChain> T.enableSettingsSync(
  enableCrossIdeSync: Boolean = false,
  action: EnableSettingSyncOptions = EnableSettingSyncOptions.NONE,
): T = apply {
  addCommand("${CMD_PREFIX}enableSettingsSync ${enableCrossIdeSync} ${action.name}")
}

fun <T : CommandChain> T.getSettingsFromServer(): T = apply {
  addCommand("${CMD_PREFIX}getSettingsFromServer")
}

@Suppress("unused")
fun <T : CommandChain> T.pushSettingsToServer(): T = apply {
  addCommand("${CMD_PREFIX}pushSettingsToServer")
}

fun <T : CommandChain> T.disableSettingsSync(deleteSettings: Boolean = false): T = apply {
  addCommand("${CMD_PREFIX}disableSettingsSync ${deleteSettings}")
}

fun <T : CommandChain> T.acceptDecompileNotice(): T = apply {
  addCommand("${CMD_PREFIX}acceptDecompileNotice")
}

fun <T : CommandChain> T.startNameSuggestionBenchmark(): T = apply {
  addCommand("${CMD_PREFIX}startNameSuggestionBenchmark")
}

fun <T : CommandChain> T.stopNameSuggestionBenchmark(reportPath: String): T = apply {
  addCommand("${CMD_PREFIX}stopNameSuggestionBenchmark $reportPath")

}

fun <T : CommandChain> T.registerCompletionMockResponse(code: String, language: String): T = apply {
  addCommand(
    "${CMD_PREFIX}registerCompletionMockResponse -code ${code.replace(System.lineSeparator(), "<newLine>")} | -language ${language}")
}

fun <T : CommandChain> T.waitInlineCompletion(): T = apply {
  addCommand("${CMD_PREFIX}waitInlineCompletion")
}

fun <T : CommandChain> T.logInlineCompletion(): T = apply {
  addCommand("${CMD_PREFIX}logInlineCompletion")
}

fun <T : CommandChain> T.waitInlineCompletionWarmup(): T = apply {
  addCommand("${CMD_PREFIX}waitInlineCompletion WARMUP")
}

fun <T : CommandChain> T.clearLLMInlineCompletionCache(): T = apply {
  addCommand("${CMD_PREFIX}clearLLMInlineCompletionCache")
}

fun <T : CommandChain> T.waitForVcsLogUpdate(): T = apply {
  addCommand("${CMD_PREFIX}waitForVcsLogUpdate")
}

/**
 * Will wait and throw exception if the condition wasn't satisfied
 */
fun <T : CommandChain> T.waitVcsLogIndexing(timeout: Duration? = null): T = apply {
  if (timeout != null) {
    addCommand("${CMD_PREFIX}waitVcsLogIndexing ${timeout}")
  }
  else {
    addCommand("${CMD_PREFIX}waitVcsLogIndexing")
  }
}

fun <T : CommandChain> T.showRecentFiles(secondsToWaitTillClose: Int): T = apply {
  addCommand("${CMD_PREFIX}showRecentFiles ${secondsToWaitTillClose}")
}

fun <T : CommandChain> T.setRegistryValue(key: String, value: String): T = apply {
  addCommand("${CMD_PREFIX}set ${key}=${value}")
}

fun <T : CommandChain> T.collectFilesNotMarkedAsIndex(): T = apply {
  addCommand("${CMD_PREFIX}collectFilesNotMarkedAsIndex")
}

fun <T : CommandChain> T.gitCommitFile(pathToFile: String, commitMessage: String): T = apply {
  addCommand("${CMD_PREFIX}gitCommit ${pathToFile},${commitMessage}")
}

fun <T : CommandChain> T.gitRollbackFile(pathToFile: String): T = apply {
  addCommand("${CMD_PREFIX}gitRollback ${pathToFile}")
}

fun <T : CommandChain> T.replaceText(startOffset: Int? = null, endOffset: Int? = null, newText: String? = null): T = apply {
  val options = StringBuilder()
  if (startOffset != null) {
    options.append(" -startOffset ${startOffset}")
  }
  if (endOffset != null) {
    options.append(" -endOffset ${endOffset}")
  }
  if (newText != null) {
    options.append(" -newText ${newText}")
  }
  addCommand("${CMD_PREFIX}replaceText ${options}")
}

fun <T : CommandChain> T.saveDocumentsAndSettings(): T = apply {
  addCommand("${CMD_PREFIX}saveDocumentsAndSettings")
}

fun <T : CommandChain> T.freezeUI(durationOfFreezeInMs: Int): T = apply {
  addCommand("${CMD_PREFIX}freezeUI $durationOfFreezeInMs")
}

fun <T : CommandChain> T.moveCaret(text: String): T = apply {
  addCommand("${CMD_PREFIX}moveCaret $text")
}

fun <T : CommandChain> T.startNewLine(): T = apply {
  executeEditorAction("EditorStartNewLine")
}

@Suppress("unused")
fun <T : CommandChain> T.captureMemoryMetrics(suffix: String): T = apply {
  addCommand("${CMD_PREFIX}captureMemoryMetrics $suffix")
}

fun <T : CommandChain> T.sleep(timeOut: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): T = apply {
  addCommand("${CMD_PREFIX}sleep ${unit.toMillis(timeOut)}")
}

fun <T : CommandChain> T.waitForEDTQueueUnstuck(): T = apply {
  addCommand("${CMD_PREFIX}waitForEDTQueueUnstuck")
}

fun <T : CommandChain> T.repeatCommand(times: Int, commandChain: (CommandChain) -> Unit): T = apply {
  repeat(times) {
    commandChain.invoke(this)
  }
}

fun <T : CommandChain> T.createScratchFile(filename: String, content: String): T = apply {
  val modifiedContent = content.replace("\n", "\\n").replace(" ", "_")
  addCommand("${CMD_PREFIX}createScratchFile $filename $modifiedContent")
}

fun <T : CommandChain> T.disableKotlinNotification(): T = apply {
  addCommand("${CMD_PREFIX}disableKotlinNotification")
}

fun <T : CommandChain> T.scrollEditor(): T = apply {
  addCommand("${CMD_PREFIX}scrollEditor")
}


/**
 * Assert that the caret is located at the specified position.
 * Lines and columns are counted from 1.
 */
fun <T : CommandChain> T.assertCaretPosition(line: Int, column: Int): T = apply {
  addCommand("${CMD_PREFIX}assertCaretPosition $line $column")
}

/**
 * Assert the current file in editor.
 */
fun <T : CommandChain> T.assertCurrentFile(name: String): T = apply {
  addCommand("${CMD_PREFIX}assertCurrentFile $name")
}

/**
 * Wait till project view is ready.
 * Should be used with `context.executeRightAfterIdeOpened()`.
 */
fun <T : CommandChain> T.waitForProjectView(): T = apply {
  addCommand("${CMD_PREFIX}waitForProjectView")
}

/**
 * Expand relative path in project view
 */
fun <T : CommandChain> T.expandProjectView(relativePath: String): T = apply {
  addCommand("${CMD_PREFIX}expandProjectView $relativePath")
}

/**
 *  The first call will create and start span.
 *  The second call with the same spanName will stop span.
 * */
fun <T : CommandChain> T.handleSpan(spanName: String): T = apply {
  addCommand("${CMD_PREFIX}handleSpan $spanName")
}