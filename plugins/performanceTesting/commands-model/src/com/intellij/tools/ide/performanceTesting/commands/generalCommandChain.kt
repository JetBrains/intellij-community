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

const val WAIT_FOR_SMART_CMD_PREFIX = "${CMD_PREFIX}waitForSmart"

const val ACTION_CMD_PREFIX = "${CMD_PREFIX}action"

fun <T : CommandChain> T.waitForSmartMode(): T {
  addCommand(WAIT_FOR_SMART_CMD_PREFIX)
  return this
}

const val WAIT_FOR_DUMB_CMD_PREFIX = "${CMD_PREFIX}waitForDumb"
fun <T : CommandChain> T.waitForDumbMode(maxWaitingTimeInSec: Int): T {
  addCommand("$WAIT_FOR_DUMB_CMD_PREFIX $maxWaitingTimeInSec")
  return this
}

const val WAIT_FOR_GIT_LOG_INDEXING = "${CMD_PREFIX}waitForGitLogIndexing"

fun <T : CommandChain> T.waitForGitLogIndexing(): T {
  addCommand(WAIT_FOR_GIT_LOG_INDEXING)
  return this
}

const val WAIT_FOR_ASYNC_REFRESH = "${CMD_PREFIX}waitForAsyncRefresh"

fun <T : CommandChain> T.waitForAsyncRefresh(): T {
  addCommand(WAIT_FOR_ASYNC_REFRESH)
  return this
}

const val RECOVERY_ACTION_CMD_PREFIX = "${CMD_PREFIX}recovery"

fun <T : CommandChain> T.recoveryAction(action: RecoveryActionType): T {
  val possibleArguments = RecoveryActionType.values().map { it.name }

  require(possibleArguments.contains(action.toString())) {
    "Argument ${action} isn't allowed. Possible values: $possibleArguments"
  }

  addCommand(RECOVERY_ACTION_CMD_PREFIX, action.toString())
  return this
}

const val FLUSH_INDEXES_CMD_PREFIX = "${CMD_PREFIX}flushIndexes"

fun <T : CommandChain> T.flushIndexes(): T {
  addCommand(FLUSH_INDEXES_CMD_PREFIX)
  return this
}

const val SETUP_PROJECT_SDK_CMD_PREFIX = "${CMD_PREFIX}setupSDK"

fun <T : CommandChain> T.setupProjectSdk(sdk: SdkObject): T {
  appendRawLine("$SETUP_PROJECT_SDK_CMD_PREFIX \"${sdk.sdkName}\" \"${sdk.sdkType}\" \"${sdk.sdkPath}\"")
  return this
}

private fun <T : CommandChain> T.appendRawLine(line: String): T {
  require(!line.contains("\n")) { "Invalid line to include: $line" }
  addCommand(line)
  return this
}

fun <T : CommandChain> T.openFile(relativePath: String,
                                  timeoutInSeconds: Long = 0,
                                  suppressErrors: Boolean = false,
                                  warmup: Boolean = false,
                                  disableCodeAnalysis: Boolean = false): T {
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
  return this
}

const val OPEN_RANDOM_FILE_CMD_PREFIX = "${CMD_PREFIX}openRandomFile"
fun <T : CommandChain> T.openRandomFile(extension: String): T {
  addCommand(OPEN_RANDOM_FILE_CMD_PREFIX, extension)
  return this
}

const val OPEN_PROJECT_CMD_PREFIX = "${CMD_PREFIX}openProject"

fun <T : CommandChain> T.openProject(projectPath: Path, openInNewWindow: Boolean = true, detectProjectLeak: Boolean = false): T {
  addCommand(OPEN_PROJECT_CMD_PREFIX, projectPath.toString(), (!openInNewWindow).toString(), detectProjectLeak.toString())
  return this
}

fun <T : CommandChain> T.reopenProject(): T {
  addCommand(OPEN_PROJECT_CMD_PREFIX)
  return this
}

const val STORE_INDICES_CMD_PREFIX = "${CMD_PREFIX}storeIndices"

fun <T : CommandChain> T.storeIndices(): T {
  addCommand(STORE_INDICES_CMD_PREFIX)
  return this
}

fun <T : CommandChain> T.compareIndices(): T {
  addCommand("%compareIndices")
  return this
}

const val GO_TO_CMD_PREFIX = "${CMD_PREFIX}goto"

fun <T : CommandChain> T.goto(offset: Int): T {
  addCommand(GO_TO_CMD_PREFIX, offset.toString())
  return this
}

fun <T : CommandChain> T.goto(line: Int, column: Int): T {
  addCommand(GO_TO_CMD_PREFIX, line.toString(), column.toString())
  return this
}

fun <T : CommandChain> T.goto(goto: Pair<Int, Int>): T {
  goto(goto.first, goto.second)
  return this
}

const val GO_TO_PSI_ELEMENT_PREFIX = "${CMD_PREFIX}goToNextPsiElement"

fun <T : CommandChain> T.gotoNextPsiElement(vararg name: String): T {
  addCommand(GO_TO_PSI_ELEMENT_PREFIX, *name)
  return this
}

fun <T : CommandChain> T.gotoNextPsiElementIfExist(vararg name: String): T {
  addCommand(GO_TO_PSI_ELEMENT_PREFIX, *name, "SUPPRESS_ERROR_IF_NOT_FOUND")
  return this
}

const val GO_TO_NAMED_PSI_ELEMENT_PREFIX = "${CMD_PREFIX}goToNamedPsiElement"

fun <T : CommandChain> T.gotoNamedPsiElement(name: String, position: Position = Position.INTO): T {
  addCommand(GO_TO_NAMED_PSI_ELEMENT_PREFIX, position.name, name)
  return this
}

fun <T : CommandChain> T.gotoNamedPsiElementIfExist(name: String, position: Position = Position.INTO): T {
  addCommand(GO_TO_NAMED_PSI_ELEMENT_PREFIX, name, position.name, "SUPPRESS_ERROR_IF_NOT_FOUND")
  return this
}

fun <T : CommandChain> T.findUsages(expectedElementName: String = "", scope: String = "Project Files", warmup: Boolean = false): T {
  navigateAndFindUsages(expectedElementName, "", scope, warmup = warmup)
  return this
}

fun <T : CommandChain> T.navigateAndFindUsages(expectedElementName: String,
                                               position: String = "INTO",
                                               scope: String = "Project Files",
                                               warmup: Boolean = false): T {
  val command = mutableListOf("${CMD_PREFIX}findUsages")
  if (expectedElementName.isNotEmpty()) {
    command.add("-expectedName $expectedElementName")
  }
  if (position.isNotEmpty()) {
    command.add("-position $position")
  }
  if (scope.isNotEmpty()) {
    command.add("-scope $scope")
  }
  if (warmup) {
    command.add("WARMUP")
  }
  addCommandWithSeparator("|", *command.toTypedArray())
  return this
}

const val INSPECTION_CMD_PREFIX = "${CMD_PREFIX}inspectCode"

fun <T : CommandChain> T.inspectCode(): T {
  addCommand(INSPECTION_CMD_PREFIX)
  return this
}

const val INSPECTION_EX_CMD_PREFIX = "${CMD_PREFIX}InspectCodeEx"

fun <T : CommandChain> T.inspectCodeEx(
  scopeName: String = "",
  toolShortName: String = "",
  inspectionTrueFields: List<String> = listOf(),
  inspectionFalseFields: List<String> = listOf(),
  downloadFileUrl: String = "",
  directory: String = "",
  hideResults: Boolean = false,
): T {
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

  addCommand(INSPECTION_EX_CMD_PREFIX + resultCommand)
  return this
}

const val CODE_ANALYSIS_CMD_PREFIX = "${CMD_PREFIX}codeAnalysis"

fun <T : CommandChain> T.checkOnRedCode(): T {
  addCommand("$CODE_ANALYSIS_CMD_PREFIX ${CodeAnalysisType.CHECK_ON_RED_CODE}")
  return this
}

fun <T : CommandChain> T.checkWarnings(vararg args: String): T {
  val setArgs = args.toSet()
  val stringBuilder = StringBuilder("")
  setArgs.forEach { stringBuilder.append("$it,") }
  addCommand("$CODE_ANALYSIS_CMD_PREFIX ${CodeAnalysisType.WARNINGS_ANALYSIS} $stringBuilder")
  return this
}

fun <T : CommandChain> T.project(project: File): T {
  addCommand("%%project ${project.absolutePath}")
  return this
}

const val EXIT_APP_CMD_PREFIX = "${CMD_PREFIX}exitApp"

fun <T : CommandChain> T.exitApp(forceExit: Boolean = true): T {
  takeScreenshot("exitApp")
  addCommand(EXIT_APP_CMD_PREFIX, forceExit.toString())
  return this
}

const val EXIT_APP_WITH_TIMEOUT_CMD_PREFIX = "${CMD_PREFIX}exitAppWithTimeout"

fun <T : CommandChain> T.exitAppWithTimeout(timeoutInSeconds: Long): T {
  addCommand(EXIT_APP_WITH_TIMEOUT_CMD_PREFIX, timeoutInSeconds.toString())
  return this
}

const val START_PROFILE_CMD_PREFIX = "${CMD_PREFIX}startProfile"

fun <T : CommandChain> T.startProfile(args: String): T {
  addCommand("$START_PROFILE_CMD_PREFIX $args")
  return this
}

fun <T : CommandChain> T.startProfile(args: String, profilerParams: String): T {
  addCommand("$START_PROFILE_CMD_PREFIX $args $profilerParams")
  return this
}

const val STOP_PROFILE_CMD_PREFIX = "${CMD_PREFIX}stopProfile"

fun <T : CommandChain> T.stopProfile(args: String = "jfr"): T {
  addCommand("$STOP_PROFILE_CMD_PREFIX $args")
  return this
}

const val MEMORY_DUMP_CMD_PREFIX = "${CMD_PREFIX}memoryDump"

fun <T : CommandChain> T.memoryDump(): T {
  addCommand(MEMORY_DUMP_CMD_PREFIX)
  return this
}

fun <T : CommandChain> T.conditionalMemoryDump(targetMessageCount: Int): T {
  addCommand("${CMD_PREFIX}conditionalMemoryDumpCommand $targetMessageCount")
  return this
}

fun <T : CommandChain> T.conditionalMemoryDumpWithErrorMessage(targetMessageCount: Int): T {
  addCommand("${CMD_PREFIX}conditionalMemoryDumpCommand $targetMessageCount WITH_ERROR_MESSAGE")
  return this
}

fun <T : CommandChain> T.profileIndexing(args: String): T {
  addCommand("%%profileIndexing $args")
  return this
}

const val CORRUPT_INDEXED_CMD_PREFIX = "${CMD_PREFIX}corruptIndex"

fun <T : CommandChain> T.corruptIndexes(pathToIndexesDir: Path, additionalDir: String = ""): T {
  if (additionalDir.isEmpty()) {
    addCommand(CORRUPT_INDEXED_CMD_PREFIX, pathToIndexesDir.toString())
  }
  else {
    addCommand(CORRUPT_INDEXED_CMD_PREFIX, pathToIndexesDir.toString(), additionalDir)
  }
  return this
}

fun <T : CommandChain> T.corruptIndexPerDir(indexDir: Path): T {
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
  return this
}

fun <T : CommandChain> T.corruptIndexPerFile(indexDir: Path): T {
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
  return this
}

const val DUMP_PROJECT_FILES_CMD_PREFIX = "${CMD_PREFIX}dumpProjectFiles"

fun <T : CommandChain> T.dumpProjectFiles(): T {
  addCommand(DUMP_PROJECT_FILES_CMD_PREFIX)
  return this
}

const val COMPARE_PROJECT_FILES_CMD_PREFIX = "${CMD_PREFIX}compareProjectFiles"

fun <T : CommandChain> T.compareProjectFiles(previousDir: String): T {
  addCommand(COMPARE_PROJECT_FILES_CMD_PREFIX, previousDir)
  return this
}

const val CLEAN_CACHES_CMD_PREFIX = "${CMD_PREFIX}cleanCaches"

fun <T : CommandChain> T.cleanCaches(): T {
  addCommand(CLEAN_CACHES_CMD_PREFIX)
  return this
}

const val COMPLETION_CMD_PREFIX = "${CMD_PREFIX}doComplete"

fun <T : CommandChain> T.doComplete(completionType: CompletionType = CompletionType.BASIC): T {
  addCommand(COMPLETION_CMD_PREFIX, completionType.name)
  return this
}

fun <T : CommandChain> T.doCompleteInEvaluateExpression(completionType: CompletionType = CompletionType.BASIC): T {
  addCommand("${CMD_PREFIX}doCompleteInEvaluateExpression", completionType.name)
  return this
}

fun <T : CommandChain> T.doCompleteInEvaluateExpressionWarmup(completionType: CompletionType = CompletionType.BASIC): T {
  addCommand("${CMD_PREFIX}doCompleteInEvaluateExpression", completionType.name, WARMUP)
  return this
}

fun <T : CommandChain> T.doCompleteWarmup(completionType: CompletionType = CompletionType.BASIC): T {
  addCommand(COMPLETION_CMD_PREFIX, completionType.name, WARMUP)
  return this
}

fun <T : CommandChain> T.doComplete(times: Int): T {
  for (i in 1..times) {
    doComplete()
    pressKey(Keys.ESCAPE)
    cleanCaches()
  }
  return this
}

const val DO_HIGHLIGHTING_CMD_PREFIX = "${CMD_PREFIX}doHighlight"

fun <T : CommandChain> T.doHighlightingWarmup(): T {
  addCommand(DO_HIGHLIGHTING_CMD_PREFIX, WARMUP)
  return this
}

fun <T : CommandChain> T.doHighlighting(): T {
  addCommand(DO_HIGHLIGHTING_CMD_PREFIX)
  return this
}

const val OPEN_PROJECT_VIEW_CMD_PREFIX = "${CMD_PREFIX}openProjectView"

fun <T : CommandChain> T.openProjectView(): T {
  addCommand(OPEN_PROJECT_VIEW_CMD_PREFIX)
  return this
}

fun <T : CommandChain> T.getLibraryPathByName(name: String, path: Path): T {
  addCommand("${CMD_PREFIX}getLibraryPathByName $name,$path")
  return this
}

const val ENTER_CMD_PREFIX = "${CMD_PREFIX}pressKey"

fun <T : CommandChain> T.pressKey(key: Keys): T {
  addCommand(ENTER_CMD_PREFIX, key.name)
  return this
}

fun <T : CommandChain> T.delayType(delayMs: Int, text: String, calculateAnalyzesTime: Boolean = false): T {
  addCommand("${CMD_PREFIX}delayType", "$delayMs|$text|$calculateAnalyzesTime")
  return this
}

const val DO_LOCAL_INSPECTION_CMD_PREFIX = "${CMD_PREFIX}doLocalInspection"

fun <T : CommandChain> T.doLocalInspection(): T {
  addCommand(DO_LOCAL_INSPECTION_CMD_PREFIX)
  return this
}

fun <T : CommandChain> T.doLocalInspectionWarmup(): T {
  addCommand(DO_LOCAL_INSPECTION_CMD_PREFIX, WARMUP)
  return this
}

const val SHOW_ALT_ENTER_CMD_PREFIX = "${CMD_PREFIX}altEnter"

fun <T : CommandChain> T.altEnter(intention: String, invoke: Boolean): T {
  addCommand(SHOW_ALT_ENTER_CMD_PREFIX, "$intention|$invoke")
  return this
}

fun <T : CommandChain> T.callAltEnter(times: Int, intention: String = "", invoke: Boolean = true): T {
  for (i in 1..times) {
    altEnter(intention, invoke)
  }
  return this
}

const val CREATE_ALL_SERVICES_AND_EXTENSIONS_CMD_PREFIX = "${CMD_PREFIX}CreateAllServicesAndExtensions"

fun <T : CommandChain> T.createAllServicesAndExtensions(): T {
  addCommand(CREATE_ALL_SERVICES_AND_EXTENSIONS_CMD_PREFIX)
  return this
}

const val RUN_CONFIGURATION_CMD_PREFIX = "${CMD_PREFIX}runConfiguration"

fun <T : CommandChain> T.runConfiguration(command: String): T {
  addCommand(RUN_CONFIGURATION_CMD_PREFIX, command)
  return this
}

const val OPEN_FILE_WITH_TERMINATE_CMD_PREFIX = "${CMD_PREFIX}openFileWithTerminate"

fun <T : CommandChain> T.openFileWithTerminate(relativePath: String, terminateIdeInSeconds: Long): T {
  addCommand("$OPEN_FILE_WITH_TERMINATE_CMD_PREFIX $relativePath $terminateIdeInSeconds")
  return this
}

const val START_POWER_SAVE_CMD_PREFIX = "${CMD_PREFIX}startPowerSave"

fun <T : CommandChain> T.startPowerSave(): T {
  addCommand(START_POWER_SAVE_CMD_PREFIX)
  return this
}

const val STOP_POWER_SAVE_CMD_PREFIX = "${CMD_PREFIX}stopPowerSave"

fun <T : CommandChain> T.stopPowerSave(): T {
  addCommand(STOP_POWER_SAVE_CMD_PREFIX)
  return this
}

const val SEARCH_EVERYWHERE_CMD_PREFIX = "${CMD_PREFIX}searchEverywhere"

fun <T : CommandChain> T.searchEverywhere(tab: String = "all",
                                          textToInsert: String = "",
                                          textToType: String = "",
                                          close: Boolean = false,
                                          selectFirst: Boolean = false): T {
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
  addCommand(SEARCH_EVERYWHERE_CMD_PREFIX, "-tab $tab $closeOnOpenArgument $selectFirstArgument $argumentForTyping|$textToInsert")
  return this
}

const val SELECT_FILE_IN_PROJECT_VIEW = "${CMD_PREFIX}selectFileInProjectView"

fun <T : CommandChain> T.selectFileInProjectView(relativePath: String): T {
  addCommand(SELECT_FILE_IN_PROJECT_VIEW, relativePath)
  return this
}

const val EXPAND_PROJECT_MENU = "${CMD_PREFIX}expandProjectMenu"

fun <T : CommandChain> T.expandProjectMenu(): T {
  addCommand(EXPAND_PROJECT_MENU)
  return this
}

const val EXPAND_MAIN_MENU = "${CMD_PREFIX}expandMainMenu"

fun <T : CommandChain> T.expandMainMenu(): T {
  addCommand(EXPAND_MAIN_MENU)
  return this
}

fun <T : CommandChain> T.closeAllTabs(): T {
  addCommand("${CMD_PREFIX}closeAllTabs")
  return this
}

const val EXPAND_EDITOR_MENU = "${CMD_PREFIX}expandEditorMenu"

fun <T : CommandChain> T.expandEditorMenu(): T {
  addCommand(EXPAND_EDITOR_MENU)
  return this
}

const val TAKE_SCREENSHOT = "${CMD_PREFIX}takeScreenshot"
fun <T : CommandChain> T.takeScreenshot(path: String): T {
  addCommand(TAKE_SCREENSHOT, path)
  return this
}

const val RECORD_REGISTERED_COUNTER_GROUPS = "${CMD_PREFIX}recordRegisteredCounterGroups"
fun <T : CommandChain> T.recordRegisteredCounterGroups(): T {
  addCommand(RECORD_REGISTERED_COUNTER_GROUPS)
  return this
}

const val RECORD_STATE_COLLECTORS = "${CMD_PREFIX}recordStateCollectors"
fun <T : CommandChain> T.recordStateCollectors(): T {
  addCommand(RECORD_STATE_COLLECTORS)
  return this
}

const val RELOAD_FILES = "${CMD_PREFIX}reloadFiles"
fun <T : CommandChain> T.reloadFiles(): T {
  addCommand(RELOAD_FILES)
  return this
}

const val ADD_FILE = "${CMD_PREFIX}addFile"
fun <T : CommandChain> T.addFile(path: String, fileName: String): T {
  addCommand("$ADD_FILE $path, $fileName")
  return this
}

fun <T : CommandChain> T.call(method: KFunction<String?>, vararg args: String): T {
  val javaMethod = method.javaMethod ?: error("Failed to resolve Java Method from the declaration")
  require(Modifier.isStatic(javaMethod.modifiers)) { "Method $method must be static" }

  addCommand(CMD_PREFIX + "importCall" + " " + javaMethod.declaringClass.name)
  addCommand(CMD_PREFIX + "call" + " " + javaMethod.name + "(" + args.joinToString(", ") + ")")
  return this
}

const val DELETE_FILE = "${CMD_PREFIX}deleteFile"
fun <T : CommandChain> T.deleteFile(path: String, fileName: String): T {
  addCommand("$DELETE_FILE $path, $fileName")
  return this
}

fun <T : CommandChain> T.delay(delayMs: Int): T {
  addCommand("${CMD_PREFIX}delay $delayMs")
  return this
}

fun <T : CommandChain> T.withSystemMetrics(chain: CommandChain): T {
  if (chain == this) throw IllegalStateException("Current command chain provided")
  for (command in chain) {
    addCommand(command.storeToString(), ENABLE_SYSTEM_METRICS)
  }
  return this
}

const val SELECT_TEXT_CMD_PREFIX = "${CMD_PREFIX}selectText"

fun <T : CommandChain> T.selectText(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int): T {
  addCommand(SELECT_TEXT_CMD_PREFIX, startLine.toString(), startColumn.toString(), endLine.toString(), endColumn.toString())
  return this
}

const val SHOW_FILE_STRUCTURE_DIALOG_PREFIX = "${CMD_PREFIX}showFileStructureDialog"

fun <T : CommandChain> T.showFileStructureDialog(): T {
  addCommand(SHOW_FILE_STRUCTURE_DIALOG_PREFIX)
  return this
}

const val IMPORT_MAVEN_PROJECT_CMD_PREFIX = "${CMD_PREFIX}importMavenProject"

fun <T : CommandChain> T.importMavenProject(): T {
  addCommand(IMPORT_MAVEN_PROJECT_CMD_PREFIX)
  return this
}

fun <T : CommandChain> T.inlineRename(to: String): T {
  startInlineRename()
  delayType(150, to)
  finishInlineRename()
  return this
}

fun <T : CommandChain> T.finishInlineRename(): T {
  addCommand("${CMD_PREFIX}finishInlineRename")
  return this
}

fun <T : CommandChain> T.startInlineRename(): T {
  addCommand("${CMD_PREFIX}startInlineRename")
  return this
}

fun <T : CommandChain> T.setRegistry(registry: String, value: Boolean): T {
  addCommand("${CMD_PREFIX}set $registry=$value")
  return this
}

fun <T : CommandChain> T.collectNameSuggestionContext(file: String, offset: Int): T {
  addCommand("${CMD_PREFIX}collectNameSuggestionContext $file $offset")
  return this
}

fun <T : CommandChain> T.waitForLlmNameSuggestions(file: String, offset: Int): T {
  addCommand("${CMD_PREFIX}waitForLlmNameSuggestions $file $offset")
  return this
}

fun <T : CommandChain> T.assertOpenedFileInRoot(path: String): T {
  addCommand("${CMD_PREFIX}assertOpenedFileInRoot $path")
  return this
}

const val IMPORT_GRADLE_PROJECT_CMD_PREFIX = "${CMD_PREFIX}importGradleProject"

fun <T : CommandChain> T.importGradleProject(): T {
  this.addCommand(IMPORT_GRADLE_PROJECT_CMD_PREFIX)
  return this
}

fun <T : CommandChain> T.showEvaluateExpression(expression: String = "", performEvaluateCount: Int = 0, warmup: Boolean = false): T {
  val command = mutableListOf("${CMD_PREFIX}showEvaluateExpression")
  if (expression.isNotEmpty()) {
    command.add("-expression $expression")
  }
  command.add("-performEvaluateCount $performEvaluateCount")
  if (warmup) {
    command.add("WARMUP")
  }
  addCommandWithSeparator("|", *command.toTypedArray())
  return this
}

fun <T : CommandChain> T.executeEditorAction(action: String): T {
  this.addCommand("${CMD_PREFIX}executeEditorAction $action")
  return this
}

fun <T : CommandChain> T.copy(): T {
  this.executeEditorAction("\$Copy")
  return this
}

fun <T : CommandChain> T.past(): T {
  this.executeEditorAction("\$Paste")
  return this
}

fun <T : CommandChain> T.cut(): T {
  this.executeEditorAction("\$Cut")
  return this
}

fun <T : CommandChain> T.selectAll(): T {
  this.executeEditorAction("\$SelectAll")
  return this
}

fun <T : CommandChain> T.checkoutBranch(branch: String, newBranchName: String = branch): T {
  this.addCommand("${CMD_PREFIX}gitCheckout $branch $newBranchName")
  return this
}

const val SHOW_FILE_HISTORY = "${CMD_PREFIX}showFileHistory"

fun <T : CommandChain> T.showFileHistory(): T {
  this.addCommand(SHOW_FILE_HISTORY)
  return this
}

fun <T : CommandChain> T.assertCompletionCommand(): T {
  this.addCommand("${CMD_PREFIX}assertCompletionCommand")
  return this
}

fun <T : CommandChain> T.assertCompletionCommand(count: Int): T {
  this.addCommand("${CMD_PREFIX}assertCompletionCommand ${count}")
  return this
}

fun <T : CommandChain> T.goToDeclaration(): T {
  this.executeEditorAction("GotoDeclaration")
  return this
}

fun <T : CommandChain> T.goToDeclaration(expectedOpenedFile: String): T {
  this.executeEditorAction("GotoDeclaration expectedOpenedFile $expectedOpenedFile")
  return this
}

fun <T : CommandChain> T.collectAllFiles(extension: String): T {
  this.addCommand("${CMD_PREFIX}collectAllFiles $extension")
  return this
}

fun <T : CommandChain> T.recompileFiles(relativeFilePaths: List<String>): T {
  addCommand("${CMD_PREFIX}buildProject RECOMPILE_FILES ${relativeFilePaths.joinToString(" ")}".trim())
  return this
}

fun <T : CommandChain> T.build(moduleNames: List<String> = listOf()): T {
  addCommand("${CMD_PREFIX}buildProject BUILD ${moduleNames.joinToString(" ")}".trim())
  return this
}

fun <T : CommandChain> T.rebuild(moduleNames: List<String> = listOf()): T {
  addCommand("${CMD_PREFIX}buildProject REBUILD ${moduleNames.joinToString(" ")}".trim())
  return this
}

fun <T : CommandChain> T.syncJpsLibraries(): T {
  addCommand("${CMD_PREFIX}syncJpsLibraries")
  return this
}

//kotlin
fun <T : CommandChain> T.clearSourceCaches(): T {
  this.addCommand("${CMD_PREFIX}clearSourceCaches")
  return this
}

fun <T : CommandChain> T.clearLibraryCaches(): T {
  this.addCommand("${CMD_PREFIX}clearLibraryCaches")
  return this
}

fun <T : CommandChain> T.performGC(): T {
  this.addCommand("${CMD_PREFIX}performGC")
  return this
}

fun <T : CommandChain> T.convertJavaToKotlinByDefault(value: Boolean): T {
  this.addCommand("${CMD_PREFIX}changeKotlinEditorOptions donTShowConversionDialog $value")
  return this
}

fun <T : CommandChain> T.assertOpenedKotlinFileInRoot(path: String): T {
  addCommand("${CMD_PREFIX}assertOpenedKotlinFileInRoot $path")
  return this
}

fun <T : CommandChain> T.assertFindUsagesCount(count: Int): T {
  addCommand("${CMD_PREFIX}assertFindUsagesCommand $count")
  return this
}

fun <T : CommandChain> T.setBreakpoint(line: Int, relativePath: String? = null, isLambdaBreakpoint: Boolean = false): T {
  addCommand("${CMD_PREFIX}setBreakpoint $line" +
             if (relativePath != null) ", $relativePath"
             else "" +
                  if (isLambdaBreakpoint) ", lambda-type" else "")
  return this
}

fun <T : CommandChain> T.removeAllBreakpoints(): T {
  addCommand("${CMD_PREFIX}removeBreakpoint all")
  return this
}

fun <T : CommandChain> T.debugRunConfiguration(runConfigurationName: String, maxWaitingTimeInSec: Int? = null): T {
  addCommand("${CMD_PREFIX}debugRunConfiguration $runConfigurationName" +
             if (maxWaitingTimeInSec != null) ",$maxWaitingTimeInSec" else "")
  return this
}

fun <T : CommandChain> T.debugStep(debugStepTypes: DebugStepTypes): T {
  addCommand("${CMD_PREFIX}debugStep ${debugStepTypes.name}")
  return this
}

fun <T : CommandChain> T.stopDebugProcess(): T {
  addCommand("${CMD_PREFIX}stopDebugProcess")
  return this
}

fun <T : CommandChain> T.waitForCodeAnalysisFinished(): T {
  addCommand("${CMD_PREFIX}waitForFinishedCodeAnalysis")
  return this
}

fun <T : CommandChain> T.checkChatBotResponse(textToCheck: String): T {
  addCommand("${CMD_PREFIX}checkResponseContains ${textToCheck}")
  return this
}

fun <T : CommandChain> T.authenticateInGrazie(token: String): T {
  addCommand("${CMD_PREFIX}authenticateInGrazie ${token}")
  return this
}

fun <T : CommandChain> T.createJavaFile(fileName: String, filePath: String, fileType: String): T {
  addCommand("${CMD_PREFIX}createJavaFile $fileName,$filePath,$fileType")
  return this
}

fun <T : CommandChain> T.createKotlinFile(fileName: String, filePath: String, fileType: String): T {
  addCommand("${CMD_PREFIX}createKotlinFile $fileName,$filePath,$fileType")
  return this
}

enum class EnableSettingSyncOptions {
  GET, PUSH, NONE
}

fun <T : CommandChain> T.enableSettingsSync(enableCrossIdeSync: Boolean = false,
                                            action: EnableSettingSyncOptions = EnableSettingSyncOptions.NONE): T {
  addCommand("${CMD_PREFIX}enableSettingsSync ${enableCrossIdeSync} ${action.name}")
  return this
}

fun <T : CommandChain> T.getSettingsFromServer(): T {
  addCommand("${CMD_PREFIX}getSettingsFromServer")
  return this
}

fun <T : CommandChain> T.pushSettingsToServer(): T {
  addCommand("${CMD_PREFIX}pushSettingsToServer")
  return this
}

fun <T : CommandChain> T.disableSettingsSync(deleteSettings: Boolean = false): T {
  addCommand("${CMD_PREFIX}disableSettingsSync ${deleteSettings}")
  return this
}

fun <T : CommandChain> T.acceptDecompileNotice(): T {
  addCommand("${CMD_PREFIX}acceptDecompileNotice")
  return this
}

fun <T : CommandChain> T.startNameSuggestionBenchmark(): T {
  addCommand("${CMD_PREFIX}startNameSuggestionBenchmark")
  return this
}

fun <T : CommandChain> T.stopNameSuggestionBenchmark(reportPath: String): T {
  addCommand("${CMD_PREFIX}stopNameSuggestionBenchmark $reportPath")
  return this
}

/**
 * Will wait and throw exception if the condition wasn't satisfied
 */
@Suppress("unused")
fun <T : CommandChain> T.waitVcsLogIndexing(timeout: Duration): T {
  addCommand("${CMD_PREFIX}waitVcsLogIndexing $timeout")
  return this
}

/**
 * Will wait infinitely till timeout of the test occurred
 */
fun <T : CommandChain> T.waitVcsLogIndexing(): T {
  addCommand("${CMD_PREFIX}waitVcsLogIndexing")
  return this
}

fun <T : CommandChain> T.disableCodeVision(): T {
  addCommand("${CMD_PREFIX}disableCodeVision")
  return this
}

fun <T : CommandChain> T.showRecentFiles(secondsToWaitTillClose: Int): T {
  addCommand("${CMD_PREFIX}showRecentFiles $secondsToWaitTillClose")
  return this
}

fun <T : CommandChain> T.setRegistryValue(key: String, value: String): T = apply {
  addCommand("${CMD_PREFIX}set $key=$value")
}

fun <T : CommandChain> T.collectFilesNotMarkedAsIndex(): T = apply {
  addCommand("${CMD_PREFIX}collectFilesNotMarkedAsIndex")
}

fun <T : CommandChain> T.gitCommitFile(pathToFile: String, commitMessage: String) = addCommand(
  "${CMD_PREFIX}gitCommit $pathToFile,$commitMessage")

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

fun <T : CommandChain> T.saveDocumentsAndSettings(): T {
  addCommand("${CMD_PREFIX}saveDocumentsAndSettings")
  return this
}

fun <T : CommandChain> T.freezeUI(durationOfFreezeInMs: Int): T {
  addCommand("${CMD_PREFIX}freezeUI $durationOfFreezeInMs")
  return this
}

fun <T : CommandChain> T.moveCaret(text: String): T {
  addCommand("${CMD_PREFIX}moveCaret $text")
  return this
}

fun <T : CommandChain> T.startNewLine(): T {
  executeEditorAction("EditorStartNewLine")
  return this
}