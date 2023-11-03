package com.intellij.tools.ide.performanceTesting.commands

import java.io.File
import java.lang.reflect.Modifier
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import kotlin.time.Duration

private const val CMD_PREFIX = '%'

const val WARMUP = "WARMUP"
const val ENABLE_SYSTEM_METRICS = "ENABLE_SYSTEM_METRICS"

fun <T : CommandChain> T.waitForSmartMode(): T = apply {
  addCommand("${CMD_PREFIX}waitForSmart")
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

fun <T : CommandChain> T.openFile(relativePath: String,
                                  timeoutInSeconds: Long = 0,
                                  suppressErrors: Boolean = false,
                                  warmup: Boolean = false,
                                  disableCodeAnalysis: Boolean = false): T = apply {
  val command = mutableListOf("${CMD_PREFIX}openFile", "-file $relativePath")
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

  addCommand(*command.toTypedArray())
}

fun <T : CommandChain> T.openRandomFile(extension: String): T = apply {
  addCommand("${CMD_PREFIX}openRandomFile", extension)
}

fun <T : CommandChain> T.openProject(projectPath: Path, openInNewWindow: Boolean = true, detectProjectLeak: Boolean = false): T = apply {
  addCommand("${CMD_PREFIX}openProject", projectPath.toString(), (!openInNewWindow).toString(), detectProjectLeak.toString())
}

fun <T : CommandChain> T.reopenProject(): T = apply {
  addCommand("${CMD_PREFIX}openProject")
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

fun <T : CommandChain> T.navigateAndFindUsages(expectedElementName: String,
                                               position: String = "INTO",
                                               scope: String = "Project Files",
                                               warmup: Boolean = false): T = apply {
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

fun <T : CommandChain> T.startProfile(args: String, profilerParams: String): T = apply {
  addCommand("${CMD_PREFIX}startProfile $args $profilerParams")
}

fun <T : CommandChain> T.stopProfile(args: String = "jfr"): T = apply {
  addCommand("${CMD_PREFIX}stopProfile $args")
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
  for (i in 1..times) {
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

fun <T : CommandChain> T.pressKey(key: Keys): T = apply {
  addCommand("${CMD_PREFIX}pressKey", key.name)
}

fun <T : CommandChain> T.delayType(delayMs: Int, text: String, calculateAnalyzesTime: Boolean = false): T = apply {
  addCommand("${CMD_PREFIX}delayType", "$delayMs|$text|$calculateAnalyzesTime")
}

fun <T : CommandChain> T.doLocalInspection(): T = apply {
  addCommand("${CMD_PREFIX}doLocalInspection")
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
  for (i in 1..times) {
    altEnter(intention, invoke)
  }
}

fun <T : CommandChain> T.createAllServicesAndExtensions(): T = apply {
  addCommand("${CMD_PREFIX}CreateAllServicesAndExtensions")
}

fun <T : CommandChain> T.runConfiguration(command: String): T = apply {
  addCommand("${CMD_PREFIX}runConfiguration", command)
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

fun <T : CommandChain> T.searchEverywhere(tab: String = "all",
                                          textToInsert: String = "",
                                          textToType: String = "",
                                          close: Boolean = false,
                                          selectFirst: Boolean = false): T = apply {
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
  if (selectFirstArgument.isNotEmpty() && closeOnOpenArgument.isNotEmpty()) {
    throw Exception("selectFirst=true argument will be ignored since close=true and SE will be closed first")
  }
  addCommand("${CMD_PREFIX}searchEverywhere", "-tab $tab $closeOnOpenArgument $selectFirstArgument $argumentForTyping|$textToInsert")
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

fun <T : CommandChain> T.reloadFiles(filePaths: List<String> = listOf()): T = apply {
  addCommand("${CMD_PREFIX}reloadFiles ${filePaths.joinToString(" ")}")
}

fun <T : CommandChain> T.addFile(path: String, fileName: String): T = apply {
  addCommand("${CMD_PREFIX}addFile ${path}, ${fileName}")
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

fun <T : CommandChain> T.linkMavenProject(projectPath: Path): T = apply {
  addCommand("${CMD_PREFIX}linkMavenProject ${projectPath}")
}

fun <T : CommandChain> T.linkGradleProject(projectPath: Path): T = apply {
  addCommand("${CMD_PREFIX}linkGradleProject ${projectPath}")
}

fun <T : CommandChain> T.unlinkGradleProject(projectPath: Path): T = apply {
  addCommand("${CMD_PREFIX}unlinkGradleProject ${projectPath}")
}

fun <T : CommandChain> T.unlinkMavenProject(projectPath: Path): T = apply {
  addCommand("${CMD_PREFIX}unlinkMavenProject ${projectPath}")
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

fun <T : CommandChain> T.showEvaluateExpression(expression: String = "", performEvaluateCount: Int = 0, warmup: Boolean = false): T = apply {
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

fun <T : CommandChain> T.selectAll(): T = apply {
  executeEditorAction("\$SelectAll")
}

fun <T : CommandChain> T.checkoutBranch(branch: String, newBranchName: String = branch): T = apply {
  addCommand("${CMD_PREFIX}gitCheckout $branch $newBranchName")
}

fun <T : CommandChain> T.showFileHistory(): T = apply {
  addCommand("${CMD_PREFIX}showFileHistory")
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

@Suppress("unused")
fun <T : CommandChain> T.goToDeclaration(): T = apply {
  executeEditorAction("GotoDeclaration")
}

fun <T : CommandChain> T.goToDeclaration(expectedOpenedFile: String): T = apply {
  executeEditorAction("GotoDeclaration expectedOpenedFile $expectedOpenedFile")
}

fun <T : CommandChain> T.collectAllFiles(extension: String): T = apply {
  addCommand("${CMD_PREFIX}collectAllFiles $extension")
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

fun <T : CommandChain> T.performGC(): T = apply {
  addCommand("${CMD_PREFIX}performGC")
}

fun <T : CommandChain> T.convertJavaToKotlinByDefault(value: Boolean): T = apply {
  addCommand("${CMD_PREFIX}changeKotlinEditorOptions donTShowConversionDialog ${value}")
}

fun <T : CommandChain> T.assertOpenedKotlinFileInRoot(path: String): T = apply {
  addCommand("${CMD_PREFIX}assertOpenedKotlinFileInRoot ${path}")
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

fun <T : CommandChain> T.checkChatBotResponse(textToCheck: String): T = apply {
  addCommand("${CMD_PREFIX}checkResponseContains ${textToCheck}")
}

fun <T : CommandChain> T.authenticateInGrazie(token: String): T = apply {
  addCommand("${CMD_PREFIX}authenticateInGrazie ${token}")
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

fun <T : CommandChain> T.enableSettingsSync(enableCrossIdeSync: Boolean = false,
                                            action: EnableSettingSyncOptions = EnableSettingSyncOptions.NONE): T = apply {
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

fun <T : CommandChain> T.disableCodeVision(): T = apply {
  addCommand("${CMD_PREFIX}disableCodeVision")
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
