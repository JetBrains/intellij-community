# Writing MCP Tools

## TLDR

An **MCP tool** in this plugin is a Kotlin `suspend` function exposed to MCP clients (Claude Code, Codex, Junie, …) through the IntelliJ MCP
server runtime. You write an ordinary method, annotate it, and the framework takes care of JSON schema generation, dispatch, error handling,
cancellation, and side-effect tracking.

This README is the canonical reference for plugin authors — both in-tree (adding tools here) and downstream (adding tools from another
IntelliJ plugin). If you want a one-page summary, jump to the [cheat sheet](#16-cheat-sheet--checklist).

---

## Table of contents

1. [Overview & quick start](#1-overview--quick-start)
2. [Registering a toolset](#2-registering-a-toolset)
3. [Writing a tool method (annotations)](#3-writing-a-tool-method-annotations)
4. [Input parameters](#4-input-parameters)
5. [Returning results](#5-returning-results)
6. [Error handling](#6-error-handling)
7. [Accessing the project and coroutine context](#7-accessing-the-project-and-coroutine-context)
8. [Threading](#8-threading)
9. [Long-running operations](#9-long-running-operations)
10. [File paths & VFS](#10-file-paths--vfs)
11. [Tool hints](#11-tool-hints)
12. [Experimental tools & filtering](#12-experimental-tools--filtering)
13. [Naming & description style](#13-naming--description-style)
14. [Testing your tool](#14-testing-your-tool)
15. [Advanced](#15-advanced)
16. [Cheat sheet / checklist](#16-cheat-sheet--checklist)
17. [Implementation details](#17-implementation-details)

Sections 1–16 are the **user guide** — everything you need to write a tool. Section 17 is **implementation details** for readers who want to
understand how the framework turns a Kotlin method into an MCP tool, or who plan to extend the framework itself.

---

## 1. Overview & quick start

### 1.1 The 30-second "Hello toolset"

Here is the absolute minimum: a class implementing `McpToolset`, one `@McpTool`-annotated `suspend fun`, and one line of `plugin.xml`:

```kotlin
class HelloToolset : McpToolset {
  @McpTool
  @McpDescription("Greets the caller by name.")
  suspend fun say_hi(
    @McpDescription("Name to greet. Defaults to 'world'.")
    name: String = "world",
  ): String = "Hello, $name!"
}
```

```xml

<extensions defaultExtensionNs="com.intellij">
  <mcpServer.mcpToolset implementation="my.plugin.HelloToolset"/>
</extensions>
```

That is the whole surface. No JSON schema by hand, no argument decoding, no result wrapping.

### 1.2 Where tools live

General-purpose tools sit under [`src/com/intellij/mcpserver/toolsets/general/`](src/com/intellij/mcpserver/toolsets/general). Optional
toolsets depending on other plugins are registered via a secondary XML and guarded by `<depends optional>`:

- [`mcpServer-terminal.xml`](resources/META-INF/mcpServer-terminal.xml) — terminal tools, depends on the Terminal plugin.
- [`mcpServer-vcs.xml`](resources/META-INF/mcpServer-vcs.xml) — Git tools, depends on Git4Idea.

---

## 2. Registering a toolset

### 2.1 Extension point

Toolsets are registered against `com.intellij.mcpServer.mcpToolset`. The declaration from [`plugin.xml`](resources/META-INF/plugin.xml):

```xml

<extensionPoint name="mcpToolset"
                interface="com.intellij.mcpserver.McpToolset"
                dynamic="true"/>
```

`dynamic="true"` means the platform can load and unload toolsets at runtime (e.g., when a plugin is installed or disabled). A registration
line looks like:

```xml

<mcpServer.mcpToolset implementation="com.intellij.mcpserver.toolsets.general.ReadToolset"/>
```

### 2.2 Optional / conditional toolsets

If your toolset depends on another plugin, register it through a dedicated XML and reference it from the root `plugin.xml`:

```xml
<!-- plugin.xml -->
<depends config-file="mcpServer-terminal.xml" optional="true">org.jetbrains.plugins.terminal</depends>
<depends config-file="mcpServer-vcs.xml" optional="true">Git4Idea</depends>
```

The secondary XML registers the toolset only when the optional plugin is present. For the general model — plugin IDs, module dependencies,
and when to prefer `<depends optional>` over `<dependencies><module/>` — see the IntelliJ Platform docs
on [plugin dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)
and [plugin configuration files](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html).

### 2.3 Lifecycle hooks

[`McpToolset`](src/com/intellij/mcpserver/McpToolset.kt) offers three overridable hooks:

```kotlin
fun isEnabled(): Boolean = true          // completely hide the toolset
fun isExperimental(): Boolean = true     // default: experimental; stable toolsets opt out
fun alwaysIncluded(): Boolean = false    // always visible in tool-router mode
```

- `isEnabled()` — gate the whole toolset behind a runtime check (feature flag, registry key).
- `isExperimental()` — **defaults to `true`**. Override to `false` only after you have tuned the tools' descriptions and observed them in
  real LLM sessions.
- `alwaysIncluded()` — force-include the toolset in the reduced tool set used by tool-router mode. Use sparingly: every always-included tool
  pollutes the shared LLM context.

---

## 3. Writing a tool method (annotations)

All three annotations live in [`annotations.kt`](src/com/intellij/mcpserver/annotations/annotations.kt).

### 3.1 `@McpTool` — declare a tool

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class McpTool(
  val name: String = "",   // custom wire name; defaults to the function name
  val title: String = "",  // optional human-readable title
)
```

The function name becomes the tool name on the wire. Override `name=` only when the Kotlin name conflicts with something (e.g., a Kotlin
keyword). Since snake_case tool names contain underscores, files with tools typically suppress the Kotlin style warning:

```kotlin
@file:Suppress("FunctionName", "unused")
```

See [`ReadToolset.kt:1`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt) for a real example.

### 3.2 `@McpDescription` — the primary LLM-facing documentation

```kotlin
@Target(
  AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.PROPERTY, AnnotationTarget.TYPE
)
annotation class McpDescription(val description: String)
```

Targets: the tool function, every value parameter, and (with `@property:McpDescription`) data-class fields. Multi-line raw strings are
idiomatic; the framework trims leading margins (`|`) consistently.

```kotlin
@McpTool
@McpDescription(
  """
    |Analyzes the specified files for errors and warnings using IntelliJ's inspections.
    |Use this tool to lint several files after editing them.
    |Returns per-file problems with severity, description, and location information.
"""
)
suspend fun lint_files(
  @McpDescription("List of project-relative file paths to analyze. Duplicate paths are ignored after normalization.")
  files: List<String>,
  /* … */
): LintFilesResult
```

From [`AnalysisToolset.kt:60-71`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt).

Treat the description as your only channel to the LLM. It must state what the tool does, its side effects, coordinate conventions (1-based
vs 0-based, inclusive vs exclusive), and every meaningful default — see [§13](#13-naming--description-style).

### 3.3 `@McpToolHints` — capability hints

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class McpToolHints(
  val readOnlyHint: McpToolHintValue = UNSPECIFIED,
  val destructiveHint: McpToolHintValue = UNSPECIFIED,
  val idempotentHint: McpToolHintValue = UNSPECIFIED,
  val openWorldHint: McpToolHintValue = UNSPECIFIED,
)
enum class McpToolHintValue { UNSPECIFIED, TRUE, FALSE }
```

Most read-only inspectors use the combination below:

```kotlin
@McpToolHints(readOnlyHint = TRUE, openWorldHint = FALSE)
@McpTool
@McpDescription("…")
suspend fun read_file(…)
```

See [`ReadToolset.kt:56`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt) and [
`AnalysisToolset.kt:59`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt). `UNSPECIFIED` values are omitted on the wire — the
default is "we don't claim anything about this dimension." See [§11](#11-tool-hints) for guidance per tool kind.

---

## 4. Input parameters

### 4.1 Supported parameter types

- Primitives: `Int`, `Long`, `Double`, `Boolean`, `Char`, etc.
- `String`.
- Nullable variants of any of the above.
- `List<T>`, `Map<K, V>`, and any `kotlinx.serialization`-`@Serializable` class.

`kotlinx.serialization` is the serialization engine. Recursive types are not supported ([
`McpToolset.kt:27`](src/com/intellij/mcpserver/McpToolset.kt)). For details on how JSON schemas are generated from parameter types,
see [§17.2](#172-schema-generation).

### 4.2 Default values make a parameter optional

Any Kotlin default turns the parameter into an optional JSON-schema property. Prefer explicit defaults to bare-nullable parameters:

```kotlin
suspend fun read_file(
  @McpDescription(Constants.FILE_PATH_DESCRIPTION)
  file_path: String,
  @McpDescription("1-based line number to start reading from")
  offset: Int = 1,
  @McpDescription("Maximum number of lines to return")
  limit: Int = 2000,
  /* … */
): String
```

From [`ReadToolset.kt:44-50`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt).

> **Important:** the framework only propagates the *optional* flag to the JSON schema — the actual default *value* is **not** emitted
> because it's not stored as metadata in bytecode. If you want the LLM to see a default, **write it into the `@McpDescription` text manually
**, e.g. `"Maximum number of lines to return (default: 2000)"`. This is a common omission in new tools. See [§17.2](#172-schema-generation)
> for the mechanism.

### 4.3 Reuse description constants

Shared wording for common parameters (file paths, timeouts) lives in [`Constants.kt`](src/com/intellij/mcpserver/toolsets/Constants.kt):

```kotlin
const val RELATIVE_PATH_IN_PROJECT_DESCRIPTION = "Path relative to the project root"
const val FILE_PATH_DESCRIPTION =
  "Path to the file. Supports project-relative paths, paths with '..', absolute paths, archive entries like '/path/lib.jar!/pkg/Foo.class', and URLs such as 'file://', 'jar://', and 'jrt://'. …"
const val TIMEOUT_MILLISECONDS_DESCRIPTION = "Timeout in milliseconds"
const val TIMED_OUT_DESCRIPTION =
  "Indicates whether the operation was timed out. 'true' value may mean that the results may be incomplete or partial. …"
const val LONG_TIMEOUT_MILLISECONDS_VALUE = 60 * 1000
const val MEDIUM_TIMEOUT_MILLISECONDS_VALUE = 10 * 1000
const val SHORT_TIMEOUT_MILLISECONDS_VALUE = 1 * 1000
```

Use them in every new tool instead of re-typing the same text:

```kotlin
@McpDescription(Constants.FILE_PATH_DESCRIPTION) file_path: String,
@McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
```

### 4.4 `@EncodeDefault` on data-class inputs/outputs

When a `@Serializable` data class has a default value for a field, opt it out of the wire payload with
`@EncodeDefault(EncodeDefault.Mode.NEVER)`:

```kotlin
@Serializable
data class LintFileResult(
  val filePath: String,
  @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
  val problems: List<LintProblem> = emptyList(),
  @property:McpDescription(Constants.TIMED_OUT_DESCRIPTION)
  @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
  val timedOut: Boolean? = false,
)
```

From [`AnalysisToolset.kt:405-413`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt). The file needs
`@file:OptIn(ExperimentalSerializationApi::class)`.

**Why:** fields equal to their default are omitted from JSON, keeping the payload minimal and reducing noise for the LLM.

Use `Mode.ALWAYS` for fields that must always appear (`problems` in the example above — always-emitted empty list is clearer than a missing
field).

### 4.5 The implicit `projectPath` parameter

Do **not** declare `projectPath` yourself — the framework injects it automatically. Consumers see a `projectPath` parameter on every tool;
see [§7](#7-accessing-the-project-and-coroutine-context) for how resolution works.

### 4.6 Naming: camelCase vs snake_case

Parameter names pass through unchanged to JSON. The codebase uses both styles — for example `file_path` in [
`ReadToolset.kt:68`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt) vs `filePath` in [
`CodeInsightToolset.kt:48`](src/com/intellij/mcpserver/toolsets/general/CodeInsightToolset.kt). **Prefer `snake_case` for new tools** (LLMs
read it slightly better), but don't rename existing parameters — it's a breaking change on the wire.

---

## 5. Returning results

### 5.1 Return-type rules

| Return type                                                  | Rendered as                    |
|--------------------------------------------------------------|--------------------------------|
| `Unit`                                                       | `[success]`                    |
| `null` (any type)                                            | `[null]`                       |
| `String`                                                     | raw text (no enclosing quotes) |
| `Char`                                                       | `'c'` (single-quoted)          |
| `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double` | `.toString()`                  |
| `@Serializable` class                                        | JSON body + structured content |
| `McpToolCallResult` / `McpToolCallResultContent`             | pass-through                   |

The rendering logic lives in [`ReflectionCallableMcpTool.kt:14-22`](src/com/intellij/mcpserver/impl/ReflectionCallableMcpTool.kt); the
public contract is in the KDoc of [`McpToolset.kt:17-24`](src/com/intellij/mcpserver/McpToolset.kt).

### 5.2 Plain text / numbered lines

Simplest and most common. `read_file` returns a `String`:

```kotlin
suspend fun read_file(/* … */): String { /* … */
}
```

For large outputs always cap with a parameter like `limit`; see [§15.2](#152-paginated--truncated-output).

### 5.3 `@Serializable` data class

The framework serializes the class to JSON and attaches it as `structuredContent` too. Decorate fields with `@property:McpDescription` so
the LLM can reason about each field:

```kotlin
@Serializable
data class BuildProjectResult(
  @property:McpDescription("Whether the build was successful")
  val isSuccess: Boolean?,
  @property:McpDescription("A list of problems encountered during the build. May be empty if the build was successful.")
  val problems: List<ProjectProblem>,
  @property:McpDescription(Constants.TIMED_OUT_DESCRIPTION)
  @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
  val timedOut: Boolean? = false,
)
```

From [`AnalysisToolset.kt:463-472`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt).

### 5.4 `Unit` for side-effect tools

A mutating tool that has no meaningful result can just return `Unit`:

```kotlin
suspend fun create_new_file(/* … */) {
  /* perform mutation */
}
```

The client sees `[success]`.

### 5.5 `McpToolCallResult` — full control

When you need multiple content blocks, an explicit `isError`, or a hand-crafted `structuredContent`, return `McpToolCallResult` directly.
Factory methods live on the companion object:

```kotlin
// from src/com/intellij/mcpserver/McpTool.kt
fun text(text: String, structuredContent: JsonObject? = null): McpToolCallResult
fun error(errorMessage: String, structuredContent: JsonObject? = null): McpToolCallResult
```

Prefer `mcpFail(...)` (see [§6](#6-error-handling)) over manually returning `McpToolCallResult.error(...)` — it produces the same wire
response and reads better in call sites.

---

## 6. Error handling

### 6.1 `mcpFail(...)` — the LLM-facing error

```kotlin
open class McpExpectedError(
  val mcpErrorText: String,
  val mcpErrorStructureContent: JsonObject? = null
) : Exception(mcpErrorText)

fun mcpFail(message: String, mcpErrorStructureContent: JsonObject? = null): Nothing =
  throw McpExpectedError(message, mcpErrorStructureContent)
```

Always prefer `mcpFail` for expected error paths — bad input, missing file, path outside the project, ambiguous state. The framework unwraps
`McpExpectedError` into a well-rendered MCP error response (not a raw stack trace):

```kotlin
val file = withContext(Dispatchers.IO) { resolveReadFile(project, file_path) }
if (!readAction { isFileAccessible(project, virtualFile = file) }) {
  mcpFail("File $file_path is outside project, library, and SDK roots")
}
```

From [`ReadToolset.kt:98-101`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt).

### 6.2 Unhandled exceptions vs cancellations

- Any uncaught throwable is surfaced to the client as `"MCP tool call has failed: …"` and logged via the IDE logger.
- `TimeoutCancellationException` (raised by `withTimeout`) is reported as `"Tool call timed out"`.
- **Never catch `CancellationException`.** If you must catch broadly, re-throw it explicitly:

```kotlin
try { /* … */
}
catch (ce: CancellationException) {
  throw ce
}
catch (e: Throwable) { /* log, maybe mcpFail */
}
```

Recursive or tight loops should use cooperative checks, as in [`fs.util.kt:233-234`](src/com/intellij/mcpserver/util/fs.util.kt):

```kotlin
if (maxDepth <= 0) return
currentCoroutineContext().ensureActive()
```

### 6.3 Structured errors for machine consumers

When the client can act programmatically on a failure (for example, "multiple projects are open — here is the list"), pass a second argument
to `mcpFail`. Case study: `noSuitableProjectError` in [`McpCallInfo.kt:80-90`](src/com/intellij/mcpserver/McpCallInfo.kt):

```kotlin
return McpExpectedError(
  mcpErrorText = """$messagePrefix
        | You may specify the project path via `$projectPathParameterName` parameter when calling a tool. 
        | …""".trimMargin(),
  mcpErrorStructureContent = Json.encodeToJsonElement(projects).jsonObject,
)
```

The structured content lets the LLM re-invoke the tool with a concrete `projectPath` instead of re-reading the human-readable sentence.

---

## 7. Accessing the project and coroutine context

### 7.1 `project` vs `projectOrNull`

The active `Project` lives on the coroutine context. Extensions on `CoroutineContext` surface it:

```kotlin
// from McpCallInfo.kt
val CoroutineContext.project: Project        // throws "No project opened" if absent
val CoroutineContext.projectOrNull: Project? // returns null; throws a structured error
//   if multiple projects are open and none was picked
```

Typical usage:

```kotlin
val project = currentCoroutineContext().project
```

From [`ReadToolset.kt:97`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt). Use `projectOrNull` only when the tool genuinely
works application-wide.

### 7.2 `mcpCallInfo` — call metadata

[`McpCallInfo`](src/com/intellij/mcpserver/McpCallInfo.kt) holds the call's `callId`, `clientInfo`, resolved `mcpToolDescriptor`, raw
arguments, MCP meta, session options, and HTTP headers:

```kotlin
val callId = currentCoroutineContext().mcpCallInfo.callId
```

From [`AnalysisToolset.kt:139`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt). Useful to thread through long-running IDE
APIs that accept a correlation token (e.g. `ProjectTaskContext(callId)` — see [§15.4](#154-mcpcallinfo-and-projecttaskcontext-plumbing)).

### 7.3 `reportToolActivity(...)` — UI-visible progress

Every long-enough tool should publish a localized activity string. It surfaces in the IDE status bar and is recorded by the
`ToolCallListener` topic:

```kotlin
currentCoroutineContext().reportToolActivity(
  McpServerBundle.message("tool.activity.reading.file", file_path)
)
```

From [`ReadToolset.kt:96`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt). Always go through [
`McpServerBundle`](src/com/intellij/mcpserver/McpServerBundle.kt) so the text is translatable.

---

## 8. Threading

### 8.1 Dispatcher basics

Tool functions are `suspend`; the framework runs them on a background dispatcher. You own the threading decisions: any PSI/VFS access needs
a read action; any document mutation needs a write action or write command.

### 8.2 `readAction { … }` for PSI/VFS reads

Wrap any PSI, VFS, or project model access in the coroutine-aware `readAction` (not the blocking `ReadAction.compute`):

```kotlin
val document = withContext(Dispatchers.Default) {
  readAction {
    FileDocumentManager.getInstance().getDocument(file, project)
      ?: mcpFail("Could not get document for $file")
  }
}
```

From [`ReadToolset.kt:102-104`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt). `readAction` is cancellation-aware: the
platform can interrupt between phases.

### 8.3 `writeCommandAction(project) { … }` for document edits

Document mutations that the user should be able to undo must run inside a write command:

```kotlin
writeCommandAction(project, commandName) {
  val psiDocumentManager = PsiDocumentManager.getInstance(project)
  for (document in documents) {
    psiDocumentManager.commitDocument(document)
  }
}
```

From [`FormattingToolset.kt:66-71`](src/com/intellij/mcpserver/toolsets/general/FormattingToolset.kt). The command name appears in the undo history.
For project-model mutations that are not user-visible, use `edtWriteAction`; for writes outside the EDT, use `backgroundWriteAction`; for
read-then-edit flows use `readAndEdtWriteAction { …; value(…) }`.

### 8.4 `withContext(Dispatchers.IO)` / `Dispatchers.EDT`

- `Dispatchers.IO` for blocking filesystem or network calls (e.g., `LocalFileSystem.refreshAndFindFileByNioFile`).
- `Dispatchers.EDT` for APIs that must run on the Event Dispatch Thread (file-editor opening, some UI services).

```kotlin
val file = withContext(Dispatchers.IO) { resolveReadFile(project, file_path) }
```

From [`ReadToolset.kt:98`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt).

---

## 9. Long-running operations

### 9.1 `withTimeoutOrNull(timeout.milliseconds)`

Every long-running tool should accept a `timeout` parameter. Use `Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE` (10s) as the default or
`LONG_TIMEOUT_MILLISECONDS_VALUE` (60s) for builds. Report the timeout as a boolean flag in the result rather than throwing:

```kotlin
@McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
/* … */
val completedInTime = withTimeoutOrNull(timeout.milliseconds) {
  withBackgroundProgress(project, progressTitle, cancellable = true) {
    collectLintFiles(project, request, onFileResult)
  }
} != null
```

From [`AnalysisToolset.kt:74-75`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt) and [
`AnalysisToolset.kt:369-373`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt).

> **Note on the `timeout` parameter.** There is currently no mechanism for the tool to discover the agent's own per-call timeout, so exposing a `timeout: Int` and asking the LLM to pass a reasonable value is a pragmatic hack. In practice the number the LLM supplies rarely correlates with the agent's real deadline — the tool may return `timedOut = true` long after the agent has already given up, or finish successfully just past the agent's cutoff. There is no good solution today; keep the parameter anyway so the LLM can at least bound obviously-long operations, and always return a `timedOut` flag rather than blocking indefinitely.

### 9.2 `withBackgroundProgress(project, title, cancellable = true)`

For multi-second work that should be visible in the IDE progress bar, wrap the body in `withBackgroundProgress`. Combine it with
`withTimeoutOrNull` so both the hard timeout and a user-initiated cancel work.

### 9.3 Bridging events: `CompletableDeferred` + listeners

When an operation finishes only via a listener callback (build, process, indexing), use `CompletableDeferred<Unit>` to signal completion and
a thread-safe accumulator for intermediate results:

```kotlin
val problems = CopyOnWriteArrayList<ProjectProblem>()
val buildFinished = CompletableDeferred<Unit>()

val buildResult = withTimeoutOrNull(timeout.milliseconds) {
  coroutineScope {
    val buildViewManager = project.serviceAsync<BuildViewManager>()
    buildViewManager.addListener(BuildProgressListener { buildId, event ->
      when (event) {
        is FileMessageEvent -> {
          problems.add(/* map event */); }
        is FinishBuildEvent -> { /* collect failures */ buildFinished.complete(Unit)
        }
        /* …other event kinds… */
      }
    }, this.asDisposable())

    val task = ProjectTaskManager.getInstance(project).createAllModulesBuildTask(!rebuild, project)
    val result = ProjectTaskManager.getInstance(project).run(ProjectTaskContext(callId), task).await()
    if (buildStarted) buildFinished.await()
    result
  }
}
```

Trimmed from [`AnalysisToolset.kt:141-271`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt). Key techniques:

- `CopyOnWriteArrayList` / `ConcurrentHashMap` for lock-free accumulation from listener callbacks.
- `this.asDisposable()` auto-unregisters the listener when the coroutine scope completes.
- `ProjectTaskManager.run(...).await()` converts the IDE `Promise` into a suspending call.
- Report `timedOut = buildResult == null` in the response instead of throwing.

### 9.4 Cooperative cancellation

Inside tight loops call `Cancellation.checkCancelled()` or `currentCoroutineContext().ensureActive()` so the client can cancel and timeouts
fire promptly — see the indexed filename processing in [`SearchToolset.kt:346-348`](src/com/intellij/mcpserver/toolsets/general/SearchToolset.kt).

---

## 10. File paths & VFS

### 10.1 `Project.resolveInProject(path, throwWhenOutside = true)`

The canonical resolver in [`fs.util.kt:46`](src/com/intellij/mcpserver/util/fs.util.kt):

```kotlin
fun Project.resolveInProject(pathInProject: String, throwWhenOutside: Boolean = true): Path
```

Accepts project-relative paths, `..`, absolute paths, `file://` / `jar://` / `jrt://` URLs, and archive entries (`file.jar!/pkg/X.class`).
When `throwWhenOutside = true` (the default), paths that escape the project root trigger an `McpExpectedError`. Usage:

```kotlin
val sourcePath = project.resolveInProject(operation.path)
val sourceFile = findFile(localFileSystem, sourcePath, operation.path)
if (sourceFile.isDirectory) mcpFail("Path is not a file: ${operation.path}")
```

From [`PatchToolset.kt:104-106`](src/com/intellij/mcpserver/toolsets/general/PatchToolset.kt).

### 10.2 `VirtualFile` resolution

- `LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)` — use when the file may have been freshly written; forces a VFS refresh.
- `LocalFileSystem.getInstance().findFileByNioFile(path)` — skip the refresh when the file is already known to VFS.
- `LocalFileSystem.getInstance().refreshNioFiles(list)` — batch refresh; used in [
  `AnalysisToolset.kt:239`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt).

### 10.3 Advertise accepted formats

When a parameter takes a path, reuse `Constants.FILE_PATH_DESCRIPTION` (accepts VFS URLs, archive entries, absolute/relative) or
`Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION` (project-relative only). Picking the right constant is one of the most impactful things you
can do — LLMs repeatedly get path conventions wrong without an explicit hint.

---

## 11. Tool hints

Defaults per tool kind:

| Tool kind                              | Recommended `@McpToolHints`                                                     |
|----------------------------------------|---------------------------------------------------------------------------------|
| Read-only inspector (read/search/list) | `readOnlyHint = TRUE, openWorldHint = FALSE`                                    |
| Local mutation (edit, rename, format)  | leave `UNSPECIFIED`, optionally `destructiveHint = FALSE` if clearly reversible |
| Destructive (delete, drop, overwrite)  | `destructiveHint = TRUE`                                                        |
| External I/O (terminal, HTTP, process) | `openWorldHint = TRUE`                                                          |

- `readOnlyHint = TRUE` is an assertion: the tool does not modify the environment. Real examples: [
  `ReadToolset.kt:56`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt), [
  `AnalysisToolset.kt:59`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt), [
  `AnalysisToolset.kt:295`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt), [
  `CodeInsightToolset.kt:33`](src/com/intellij/mcpserver/toolsets/general/CodeInsightToolset.kt).
- `openWorldHint = FALSE` tells the client that the tool only touches the local IDE state — no external services.
- `UNSPECIFIED` values are omitted from the wire payload; clients treat the hint as "unknown."

---

## 12. Experimental tools & filtering

### 12.1 Experimental by default

`McpToolset.isExperimental()` returns `true` by default. Tools from experimental toolsets are hidden from tool-router mode unless explicitly
included. Override only after evaluating the toolset with real LLM traffic.

### 12.2 Registry-based mask filter

The registry value `mcp.server.tools.filter` (declared in [`plugin.xml:90-92`](resources/META-INF/plugin.xml)) filters the set of exposed tools:

```xml

<registryKey defaultValue=""
             description="Filter for MCP tools, like -*,+com.intellij.mcpserver.toolsets.general.*,-*.read_file"
             key="mcp.server.tools.filter"/>
```

Mask syntax:

- Comma-separated entries.
- Each entry prefixed with `+` (allow) or `-` (deny); no prefix means allow.
- Applied in order; the last matching mask wins.
- Example `-*,+com.intellij.mcpserver.toolsets.general.*,-*.read_file` — deny everything, re-allow `general.*`, then deny the specific
  `*.read_file`.

### 12.3 Custom filter EP

Contribute your own `McpToolFilterProvider` for feature flags, license gates, or per-user visibility:

```xml

<mcpServer.mcpToolFilterProvider implementation="my.plugin.MyFeatureFlagFilterProvider"/>
```

In-tree examples: `RegistryKeyMcpToolFilterProvider`, `SettingsBasedMcpToolFilterProvider`, `DisallowListBasedMcpToolFilterProvider` — all
registered in [`plugin.xml:61-64`](resources/META-INF/plugin.xml).

---

## 13. Naming & description style

### 13.1 Tool names

- The Kotlin method name becomes the wire name. Override via `@McpTool(name="...")` only when unavoidable.
- Prefer snake_case verbs for new tools: `build_project`, `get_symbol_info`, `apply_patch`.
- Add `@file:Suppress("FunctionName")` at the top of the file (see [
  `ReadToolset.kt:1`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt)) so Kotlin style warnings don't fire.

### 13.2 Description writing rules

Observe the patterns in [`AnalysisToolset.kt:61-68`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt) and [
`ReadToolset.kt:58-67`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt):

1. Lead with what the tool does, in imperative mood.
2. Say when to use it (and when not).
3. State side effects, pre-conditions, and units.
4. Enumerate modes / formats explicitly.
5. Mention timeout behaviour, truncation, pagination.
6. Embed the default values that the JSON schema does not carry (see [§4.2](#42-default-values-make-a-parameter-optional)).
7. Use multi-line Kotlin raw strings; the framework trims `|` margins consistently.

### 13.3 Parameter descriptions

Every parameter needs a description that states, at minimum:

- Semantics (what it means, not the type).
- Bounds: 1-based vs 0-based, inclusive vs exclusive.
- Units: milliseconds, bytes, lines.
- `null` / missing semantics.

Good examples in [`ReadToolset.kt:47-50`](src/com/intellij/mcpserver/toolsets/general/ReadToolset.kt):

```kotlin
@McpDescription("1-based line number to start reading from")
offset: Int = 1,
@McpDescription("Maximum number of lines to return")
limit: Int = DEFAULT_READ_LIMIT,
```

### 13.4 Interpolate constants into descriptions whenever you can

If a `const val` is used as a default argument value (or bakes a hard-coded cap into tool behavior), **reuse the same constant inside the `@McpDescription` via Kotlin string interpolation** instead of hand-typing the number. Otherwise the code and the docs drift the moment someone tweaks the constant:

```kotlin
// BAD — description will silently lie after someone bumps the constant
@McpDescription("Maximum number of results to return (default: 1000)")
limit: Int = Constants.MAX_LINES_COUNT_VALUE,

// GOOD — single source of truth
@McpDescription("Maximum number of results to return (default: ${Constants.MAX_LINES_COUNT_VALUE})")
limit: Int = Constants.MAX_LINES_COUNT_VALUE,
```

This works whenever the referenced value is itself a `const val` (Kotlin compile-time constant) — primitives and `String` literals. Use it everywhere it compiles: defaults for `limit` / `timeout` / `max_depth`, size bounds mentioned in descriptions, mode enumerations backed by `const val`, etc. If the referenced value is not `const` (e.g., a `val` computed at runtime), fall back to writing the number by hand and accept the drift risk — there is no runtime-string-interpolation path for `@McpDescription` arguments because annotations only accept compile-time constants.

---

## 14. Testing your tool

### 14.1 Base classes

Two JUnit 5 base classes live under [`tests/testSrc/com/intellij/mcpserver/`](tests/testSrc/com/intellij/mcpserver).

- [`McpToolsetTestBase`](tests/testSrc/com/intellij/mcpserver/McpToolsetTestBase.kt) — blank temp project; override `projectTestData()` to
  seed from a directory.
- [`GeneralMcpToolsetTestBase`](tests/testSrc/com/intellij/mcpserver/GeneralMcpToolsetTestBase.kt) — inherits the blank base, pre-seeds
  from [`testResources/mcpToolsetProject/`](tests/testResources/mcpToolsetProject) with `src/Main.java`, `src/Test.java`, `src/Class.java`,
  and exposes them as `mainJavaFile`, `testJavaFile`, `classJavaFile`.

Both use `@TestApplication` and fixture helpers (`projectFixture`, `moduleFixture`, `sourceRootFixture`, `fileOrDirInProjectFixture`).

### 14.2 `testMcpTool(...)`

Two overloads on the base class (see [`McpToolsetTestBase.kt:138-164`](tests/testSrc/com/intellij/mcpserver/McpToolsetTestBase.kt)):

```kotlin
// exact text match
protected suspend fun testMcpTool(toolName: String, input: JsonObject, output: String)

// custom assertion on the full result
protected suspend fun testMcpTool(
  toolName: String, input: JsonObject,
  resultChecker: (CallToolResult) -> Unit
)
```

Typical test:

```kotlin
class FileToolsetTest : GeneralMcpToolsetTestBase() {
  @Test
  fun create_new_file() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      FileToolset::create_new_file.name,
      buildJsonObject { put("pathInProject", JsonPrimitive("src/NewFile.java")) },
      "[success]",
    )
  }
}
```

From [`FileToolsetTest.kt:55-64`](tests/testSrc/com/intellij/mcpserver/toolsets/FileToolsetTest.kt). Reference the tool by function
reference (`FileToolset::create_new_file.name`) so renames stay in sync.

### 14.3 End-to-end fallback

`withConnection { client -> … }` spins up a real MCP server, a streamable HTTP transport, and an MCP client bound to the test project. Most
tests never touch it, but it is the escape hatch for assertions on `structuredContent`, multiple content blocks, or error payloads — see [
`McpToolsetTestBase.kt:92-126`](tests/testSrc/com/intellij/mcpserver/McpToolsetTestBase.kt).

---

## 15. Advanced

### 15.1 `McpToolsProvider` — below reflection

For dynamically generated tool sets (not backed by a fixed Kotlin class), implement [
`McpToolsProvider`](src/com/intellij/mcpserver/McpToolsProvider.kt) directly:

```kotlin
interface McpToolsProvider {
  companion object {
    val EP: ExtensionPointName<McpToolsProvider> = ExtensionPointName.create("com.intellij.mcpServer.mcpToolsProvider")
  }

  fun getTools(): List<McpTool>
}
```

Each `McpTool` needs a hand-built `McpToolDescriptor` (with a manually crafted `McpToolSchema`) and an implementation of
`suspend fun call(args: JsonObject): McpToolCallResult`. The standard `McpToolset` path uses [
`ReflectionToolsProvider`](src/com/intellij/mcpserver/impl/ReflectionToolsProvider.kt) under the hood — 99% of tools should stay on the
high-level path.

### 15.2 Paginated / truncated output

Heavy-output tools should accept `limit` / `offset` and flag truncation in the result. Pattern from [
`SearchToolset.kt:91-95`](src/com/intellij/mcpserver/toolsets/general/SearchToolset.kt):

```kotlin
suspend fun search_text(
  @McpDescription("Text to search for") q: String,
  @McpDescription(PATHS_DESCRIPTION) paths: List<String>? = null,
  @McpDescription("Maximum number of results to return") limit: Int = 1000,
): SearchResult
```

Look at [`Constants.MAX_LINES_COUNT_VALUE`](src/com/intellij/mcpserver/toolsets/Constants.kt) and
`MAX_RESULTS_UPPER_BOUND` in [`SearchToolset.kt:65`](src/com/intellij/mcpserver/toolsets/general/SearchToolset.kt) for typical caps. Use the
streaming accumulator in [`util/OutputCollector.kt`](src/com/intellij/mcpserver/util/OutputCollector.kt) when you need to trim in the middle
of growing output.

### 15.3 Side-effect tracking

Mutating tools emit events consumed by [`ToolCallListener.afterMcpToolCall`](src/com/intellij/mcpserver/ToolCallListener.kt):

```kotlin
sealed interface McpToolSideEffectEvent
class DirectoryCreatedEvent(val file: VirtualFile) : FileEvent
class DirectoryDeletedEvent(val file: VirtualFile) : FileEvent
class FileCreatedEvent(val file: VirtualFile, val content: String) : FileEvent
class FileDeletedEvent(val file: VirtualFile, val content: String?) : FileEvent
class FileMovedEvent(val file: VirtualFile, val oldParent: VirtualFile, val newParent: VirtualFile) : FileEvent
class FileContentChangeEvent(val file: VirtualFile, val oldContent: String?, val newContent: String) : FileEvent
```

Clients use these events to render diffs, implement undo, or audit changes. The framework fills them automatically from the VFS; you usually
don't emit them by hand.

### 15.4 `mcpCallInfo` and `ProjectTaskContext` plumbing

Thread `mcpCallInfo.callId` through long-running IDE APIs so the IDE can deduplicate concurrent calls and stream progress to the right
session:

```kotlin
val callId = currentCoroutineContext().mcpCallInfo.callId
/* … */
val context = ProjectTaskContext(callId)
val result = ProjectTaskManager.getInstance(project).run(context, task).await()
```

From [`AnalysisToolset.kt:139`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt) and [
`AnalysisToolset.kt:254-258`](src/com/intellij/mcpserver/toolsets/general/AnalysisToolset.kt).

---

## 16. Cheat sheet / checklist

### 16.1 Adding a new tool — 10 steps

1. Add a class implementing [`McpToolset`](src/com/intellij/mcpserver/McpToolset.kt) under `toolsets/<category>/`.
2. Register it in [`plugin.xml`](resources/META-INF/plugin.xml) (or a `<depends optional config-file>` XML for optional dependencies).
3. Annotate each method with `@McpTool` + `@McpDescription` (+ optional `@McpToolHints`).
4. Annotate every parameter with `@McpDescription`. Use defaults for optional parameters and reuse [
   `Constants.*`](src/com/intellij/mcpserver/toolsets/Constants.kt) for shared wording. **Restate default values in description text** — the
   schema does not carry them.
5. For data-class inputs/outputs: `@Serializable`, `@EncodeDefault(Mode.NEVER)` on defaulted fields, `@property:McpDescription` on each
   field, `@file:OptIn(ExperimentalSerializationApi::class)`.
6. Get the project with `currentCoroutineContext().project`. Announce work with `reportToolActivity(McpServerBundle.message(...))`.
7. Wrap PSI/VFS reads in `readAction { }`, user-visible edits in `writeCommandAction(project) { … }`, blocking IO in
   `withContext(Dispatchers.IO) { … }`.
8. For long operations: accept `timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE`, wrap the body in
   `withTimeoutOrNull(timeout.milliseconds)`, expose `timedOut` in the result.
9. Throw `mcpFail("…")` for expected errors. Never catch `CancellationException`.
10. Add a JUnit 5 test extending `GeneralMcpToolsetTestBase` and calling `testMcpTool(...)`.

### 16.2 "Do I need a write action?"

```
Reads PSI / VFS / project model      → readAction { }
Edits a Document the user could undo → writeCommandAction(project) { }
Mutates app / VFS state not backed by a Document → writeAction / backgroundWriteAction
Blocks on filesystem / network IO    → withContext(Dispatchers.IO) { }
```

### 16.3 Smells to avoid

- Returning a huge `String` without a `limit` parameter.
- Catching `CancellationException` (always rethrow or avoid catching it).
- Running blocking IO on the default dispatcher.
- Hardcoding user-facing strings instead of `McpServerBundle.message(...)`.
- Forgetting `@EncodeDefault(Mode.NEVER)` on defaulted result fields (bloats wire payload, breaks optional-ness in the schema).
- Relying on Kotlin defaults to reach the LLM — defaults are not serialized into the schema, say them in `@McpDescription`.
- Declaring a `projectPath` parameter yourself — the framework injects it.

---

## 17. Implementation details

Everything up to §16 is enough to write a tool. The sections below explain how the framework actually turns a Kotlin method into an MCP
tool. Read them when you extend the framework itself, debug a schema / serialization issue, or contribute to the plugin.

### 17.1 Tool discovery pipeline

On MCP server startup the framework walks registered toolsets and converts them into runtime `McpTool` instances:

1. [`McpToolset.EP`](src/com/intellij/mcpserver/McpToolset.kt) enumerates every registered toolset (filtered through `isEnabled()`).
2. [`ReflectionToolsProvider`](src/com/intellij/mcpserver/impl/ReflectionToolsProvider.kt) is the default `McpToolsProvider`. It calls
   `McpToolset.asTools()`, which delegates to [`toolsetReflection.util.kt`](src/com/intellij/mcpserver/impl/util/toolsetReflection.util.kt)
   and walks every public method carrying `@McpTool`.
3. For each method, `asTool(...)` builds an [`McpToolDescriptor`](src/com/intellij/mcpserver/McpToolDescriptor.kt) (name / title /
   description / category / input & output schemas / tool hints) and wraps the method in a [
   `ReflectionCallableMcpTool`](src/com/intellij/mcpserver/impl/ReflectionCallableMcpTool.kt).
4. At call time the session handler invokes `ReflectionCallableMcpTool.call(args)` which delegates to [
   `CallableBridge`](src/com/intellij/mcpserver/impl/util/CallableBridge.kt):
  - Decodes each named argument from the incoming `JsonObject` via `kotlinx.serialization` (skipping optional missing args).
  - Invokes the suspend function with `callable.callSuspendBy(argMap)`.
  - Re-encodes the return value according to the rules in [§5.1](#51-return-type-rules).
5. The session handler (see [`impl/McpSessionHandler.kt`](src/com/intellij/mcpserver/impl/McpSessionHandler.kt)) wraps the whole call:
  - Catches `McpExpectedError` → MCP error response with text + optional structured content.
  - Catches `TimeoutCancellationException` → `"Tool call timed out"`.
  - Rethrows `CancellationException` unchanged.
  - Decorates anything else as `"MCP tool call has failed: …"` and logs it.
  - Collects side-effect events emitted during the call and routes them to `ToolCallListener.afterMcpToolCall`.

99% of tools sit on top of this pipeline without touching it. The alternative entry point is [
`McpToolsProvider`](src/com/intellij/mcpserver/McpToolsProvider.kt) — see [§15.1](#151-mcptoolsprovider--below-reflection).

### 17.2 Schema generation

JSON schemas for both input parameters and return types are derived in [
`impl/util/schema.util.kt`](src/com/intellij/mcpserver/impl/util/schema.util.kt) using the [
`schemakenerator`](https://github.com/SMILEY4/schema-kenerator) library on top of `kotlinx.serialization` metadata.

**Input schema (`parametersSchema`).** For each `@McpTool` method:

- The generator walks `callable.parameters` plus the implicit `projectPath` parameter (unless a custom name is contributed via
  `McpProjectPathCustomizer`).
- Each parameter's type is analyzed via `analyzeTypeUsingKotlinxSerialization().generateJsonSchema()`, then post-processed:
  `handleCoreAnnotations` + `handleMcpDescriptionAnnotations` (reads `@McpDescription` on function parameters, honours `|`-trimmed
  multi-line strings), `removeNumericBounds` (strips tight `minimum/maximum` that some LLMs mishandle), and `addStringTypeToEnums` (ensures
  enum props advertise `"type": "string"`).
- A parameter is added to `required` **if and only if `parameter.isOptional` is false** — that is, when the Kotlin parameter has no default value. *
  *Default values themselves are never emitted into the schema** (no `default:` field). If you want the LLM to see a default, put it in the
  `@McpDescription` text (see [§4.2](#42-default-values-make-a-parameter-optional)).

**Output schema (`returnTypeSchema`).** Only generated for non-primitive, non-`Unit`, non-`McpToolCallResult` types. Same post-processing as
inputs, plus one crucial extra step: `removeRequiredForDefaultValues(jsonSchema, serializer)` walks the generated schema and matching
`SerialDescriptor` tree and removes any element whose `isElementOptional` is true from the corresponding `required` set. This
fixes [IJPL-230494](https://youtrack.jetbrains.com/issue/IJPL-230494) — without it, `@EncodeDefault(NEVER)` fields would be omitted on the
wire (because of `explicitNulls = false` in [`McpServerJson`](src/com/intellij/mcpserver/impl/util/McpServerJson.kt)) while still being
declared `required` in the schema, causing clients to reject valid responses. This is the single most important reason to use
`@EncodeDefault(Mode.NEVER)` on defaulted result fields — see [§4.4](#44-encodedefault-on-data-class-inputsoutputs).

**Optional parameter decoding.** [`CallableBridge.call`](src/com/intellij/mcpserver/impl/util/CallableBridge.kt) skips optional parameters
when the argument is absent (`if (argElement == null && parameter.isOptional) continue`); Kotlin then uses the default value. Required
missing parameters raise an internal error before the tool body runs.

### 17.3 Filter composition

Visible tools are resolved by composing every `McpToolFilterProvider` extension. Registered providers in [
`plugin.xml`](resources/META-INF/plugin.xml):

- `DisallowListBasedMcpToolFilterProvider` — hardcoded deny list for tools known to misbehave.
- `SettingsBasedMcpToolFilterProvider` — reflects the Tools → MCP Server settings UI.
- `RegistryKeyMcpToolFilterProvider` — reads the `mcp.server.tools.filter` mask (see [§12.2](#122-registry-based-mask-filter)).
- `IndividualRegistryKeyMcpToolFilterProvider` — per-tool registry overrides.

Each provider returns an [`McpToolFilter`](src/com/intellij/mcpserver/McpToolFilter.kt); the session collects all filters and a tool is
exposed only if every filter's `shouldInclude(tool)` returns true. [`MaskBased`](src/com/intellij/mcpserver/McpToolFilter.kt) parses the
mask string through [`MaskList`](src/com/intellij/mcpserver/MaskList.kt): comma-separated entries with `+` / `-` prefixes, last match wins.

### 17.4 Side-effect event pipeline

The session handler wraps every tool call with a VFS listener that captures file operations happening during the call and turns them into
the events declared in [`ToolCallListener.kt`](src/com/intellij/mcpserver/ToolCallListener.kt) — `FileCreatedEvent`, `FileDeletedEvent`,
`FileMovedEvent`, `FileContentChangeEvent`, `DirectoryCreatedEvent`, `DirectoryDeletedEvent`. After the call completes, the events are
passed to every subscriber of the application-level `ToolCallListener.TOPIC` via `afterMcpToolCall(descriptor, events, error, callInfo)`.
Tool authors do not emit these events by hand — any write that goes through the VFS (including `writeCommandAction` updates to a `Document`)
is captured automatically.

### 17.5 Project resolution

Tool-call project resolution is performed in [`McpSessionHandler.kt`](src/com/intellij/mcpserver/impl/McpSessionHandler.kt) through
[`McpProjectLocationInputs.kt`](src/com/intellij/mcpserver/impl/McpProjectLocationInputs.kt). The logic has two modes:

1. Strict mode: if the tool call contains an explicit `projectPath` argument, MCP matches only by that value.
   If it doesn't resolve to an open project, the call fails immediately with `noSuitableProjectError`.
2. Chaining mode: if `projectPath` is absent, MCP tries the following sources in order:
   1. `IJ_MCP_SERVER_PROJECT_PATH` from the current call header / request metadata.
   2. Session-level `IJ_MCP_SERVER_PROJECT_PATH` captured from the initial session request.
   3. MCP session roots (when the client advertised them).

In chaining mode every failed step is logged to `trace`, and if none of the sources resolve to an open project, MCP throws a structured
`noSuitableProjectError` listing every open project as `structuredContent`, so the client can re-invoke the tool with a concrete `projectPath`.

User-facing error text intentionally points the LLM only to `projectPath`; headers / env vars are treated as internal transport details and are
not mentioned in the error message.

Sessions that opt-in to project-path customization via `McpProjectPathCustomizer` can rename / re-describe the implicit parameter — [
`schema.util.kt`](src/com/intellij/mcpserver/impl/util/schema.util.kt) applies the customizer after the schema is built.

### 17.6 Runtime overrides (system properties)

[`McpServerRuntimeOverrideUtil.kt`](src/com/intellij/mcpserver/impl/McpServerRuntimeOverrideUtil.kt) exposes two JVM system properties that override MCP server settings at runtime. They exist primarily for evaluation / benchmarking / CI scenarios where you want to ignore user-level settings and pin the server to a predictable state.

| Property                       | Type      | Effect                                                                                         |
|--------------------------------|-----------|------------------------------------------------------------------------------------------------|
| `idea.mcp.server.force.enable` | `Boolean` | Forces the server ON regardless of the "Enable MCP server" setting. Default `false`.           |
| `idea.mcp.server.force.port`   | `Int`     | Forces the server to bind a specific port in `1..65535`. Absent property means "no override".  |

Pass them on the IDE command line, e.g. `-Didea.mcp.server.force.enable=true -Didea.mcp.server.force.port=64321`.

Implementation details:

- `isMcpServerEffectivelyEnabled(savedEnabled)` — OR-combines the persisted user setting with the force-enable property; callers that honour the "enabled" flag go through this helper.
- `getForcedMcpServerPortState()` parses the port and returns `Absent` / `Valid(port)` / `Invalid(rawValue)` so the UI can surface a clear error for malformed values instead of silently falling back.
- `hasMcpServerRuntimeOverrides()` is a quick probe used by settings UI to hide / disable controls that the system property already dictates.

These overrides are **not a public API** — they are JetBrains-internal knobs for evaluation and should not be depended on by downstream plugins.
