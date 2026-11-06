// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.deadCode.RefSerializationUtil
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.codeInspection.ex.EntryPointsManager
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.codeInspection.ex.createSimple
import com.intellij.codeInspection.reference.RefClass
import com.intellij.codeInspection.reference.RefElement
import com.intellij.codeInspection.reference.RefField
import com.intellij.codeInspection.reference.RefImplicitConstructor
import com.intellij.codeInspection.reference.RefJavaElement
import com.intellij.codeInspection.reference.RefJavaVisitor
import com.intellij.codeInspection.reference.RefMethod
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImplicitClass
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.util.SystemProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.system.exitProcess

private const val COMMAND = "psiUnused"

private const val USAGE = """Usage: $COMMAND --project=<dir> --jdk=<JDK home> --output=<unused.json> [--scope=<dir>,...]
  --project=<dir>        the project to open; a directory with .idea opens without import
  --jdk=<JDK home>       a full JDK with lib/src.zip; it is registered under every JDK name that the project asks for
  --output=<unused.json> the declarations with the answer of the unused-declaration analysis
  --scope=<dir>,...      analyze only the modules whose content roots lie under these directories of the project"""

private class UnusedOptions(@JvmField val project: Path, @JvmField val jdk: Path, @JvmField val output: Path, @JvmField val scope: List<Path>) {
  companion object {
    fun parse(args: List<String>): UnusedOptions {
      val values = HashMap<String, String>()
      for (arg in args) {
        require(arg.startsWith("--") && arg.contains('=')) { "unknown argument: $arg" }
        values[arg.substring(2, arg.indexOf('='))] = arg.substring(arg.indexOf('=') + 1)
      }
      val unknown = values.keys - setOf("project", "jdk", "output", "scope")
      require(unknown.isEmpty()) { "unknown options: $unknown" }
      val project = Path.of(requireNotNull(values["project"]) { "--project is required" }).absolute().normalize()
      val jdk = Path.of(requireNotNull(values["jdk"]) { "--jdk is required" }).absolute().normalize()
      require(Files.isRegularFile(jdk.resolve("lib/src.zip"))) { "--jdk must be a full JDK with lib/src.zip: $jdk" }
      val output = Path.of(requireNotNull(values["output"]) { "--output is required" }).absolute().normalize()
      val scope = values["scope"].orEmpty().split(',').filter { it.isNotBlank() }.map { project.resolve(it.trim()).normalize() }
      require(scope.all { it.startsWith(project) }) { "--scope must name directories of the project: $scope" }
      return UnusedOptions(project, jdk, output, scope)
    }
  }
}

/**
 * Opens a project headless and writes the answer of IntelliJ's global unused-declaration analysis (the `unused`
 * inspection, [UnusedDeclarationInspection]) for every class, method and field of its Java and Kotlin sources, the
 * Kotlin ones through UAST: the oracle of accio's `unused`.
 *
 * The analysis runs twice over the whole project, or over the modules under the `--scope` directories, tests included,
 * with the inspection's defaults otherwise:
 * - with test entry points (`dead`): a JUnit or TestNG test is an entry, so what only tests use is reachable;
 * - without them (`dead_in_production`): what only tests use is dead too.
 *
 * Per declaration the output says whether some declaration of the graph refers to it (`referenced`) and, for an entry
 * point, why it is one (`entry`), in the order [UnusedDeclarationInspectionBase.isEntryPoint] asks: the
 * [ImplicitUsageProvider] that claims it, an annotation or pattern of the [EntryPointsManager], the dead-code
 * [com.intellij.codeInspection.reference.EntryPoint] extension, `main`, an implicit class, an applet or a servlet,
 * a serialization member, else `external` (a use in a file outside the graph, such as plugin.xml, or the qualified name
 * in text). Declarations are sorted by file, offset and name, so two runs on one revision write the same bytes.
 *
 * The starter never saves the project or the application settings.
 * Run it with dedicated `idea.config.path` and `idea.system.path`, and on a copy of the project.
 * The exit code is 0 after the output is written, 1 on a failure and 2 on a usage error.
 */
internal class PsiUnusedStarter : ModernApplicationStarter() {
  override val isHeadless: Boolean = true

  override fun premain(args: List<String>) {
    System.setProperty("ide.browser.jcef.enabled", false.toString())
    System.setProperty("vcs.log.index.enable", false.toString())
    System.setProperty("ide.slow.operations.assertion", false.toString())
    SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", true.toString())
  }

  override suspend fun start(args: List<String>) {
    val options = try {
      UnusedOptions.parse(args.drop(1))
    }
    catch (e: IllegalArgumentException) {
      System.err.println("Error: ${e.message}")
      System.err.println(USAGE)
      exitProcess(2)
    }
    ApplicationManagerEx.getApplicationEx().setSaveAllowed(false)
    val exitCode = try {
      exportUnused(options)
      0
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      System.err.println("Error: the export failed")
      e.printStackTrace()
      1
    }
    exitProcess(exitCode)
  }
}

/** One declaration of the graph, keyed by its file and the offset of its name. */
private class UnusedDeclaration(
  @JvmField val file: String,
  @JvmField val offset: Int,
  @JvmField val line: Int,
  @JvmField val start: Int,
  @JvmField val end: Int,
  @JvmField val kind: String,
  @JvmField val name: String,
  @JvmField val owner: String?,
  @JvmField val language: String,
  @JvmField val test: Boolean,
  @JvmField val dead: Boolean,
  @JvmField val referenced: Boolean,
  @JvmField val entry: String?,
) {
  val key: String get() = "$file:$offset:$name"
}

private suspend fun exportUnused(options: UnusedOptions) {
  val startedAt = System.currentTimeMillis()
  val project = openReadyProject(options.project, options.jdk, ::log)
  val openMs = System.currentTimeMillis() - startedAt
  log("project is ready after $openMs ms")

  val modules = if (options.scope.isEmpty()) null else scopeModules(project, options.scope)
  val withTests = analyze(project, options.project, modules, testEntryPoints = true)
  val inProduction = analyze(project, options.project, modules, testEntryPoints = false).associateBy { it.key }
  log("${withTests.size} declarations, ${withTests.count { it.dead }} dead with test entry points, " +
      "${inProduction.values.count { it.dead }} without them")

  Files.createDirectories(options.output.parent)
  Files.newBufferedWriter(options.output).use { out ->
    out.write("{\n")
    out.write("  \"tool\": \"psi-unused\",\n")
    out.write("  \"version\": ${json(ApplicationInfo.getInstance().build.asString())},\n")
    out.write("  \"inspection\": \"unused\",\n")
    out.write("  \"declarations\": [\n")
    withTests.forEachIndexed { index, d ->
      val deadInProduction = inProduction[d.key]?.dead ?: true
      out.write("    {\"file\": ${json(d.file)}, \"line\": ${d.line}, \"start\": ${d.start}, \"end\": ${d.end}, " +
                "\"kind\": ${json(d.kind)}, \"name\": ${json(d.name)}, \"owner\": ${d.owner?.let(::json) ?: "null"}, " +
                "\"language\": ${json(d.language)}, \"test\": ${d.test}, \"dead\": ${d.dead}, " +
                "\"dead_in_production\": $deadInProduction, \"referenced\": ${d.referenced}, " +
                "\"entry\": ${d.entry?.let(::json) ?: "null"}}")
      out.write(if (index < withTests.size - 1) ",\n" else "\n")
    }
    out.write("  ]\n}\n")
  }
  log("wrote ${options.output}; total ${System.currentTimeMillis() - startedAt} ms")
}

/** The modules with a content root under one of [dirs]; each directory must hold one. */
private suspend fun scopeModules(project: Project, dirs: List<Path>): List<Module> {
  val byDir = readAction {
    val roots = ModuleManager.getInstance(project).modules.associateWith { module ->
      ModuleRootManager.getInstance(module).contentRoots.map { Path.of(it.path) }
    }
    dirs.associateWith { dir -> roots.filterValues { paths -> paths.any { it.startsWith(dir) } }.keys }
  }
  for ((dir, modules) in byDir) {
    require(modules.isNotEmpty()) { "no module under $dir" }
  }
  val modules = byDir.values.flatten().distinct().sortedBy { it.name }
  log("scope: ${modules.size} modules under ${dirs.joinToString()}: ${modules.joinToString { it.name }}")
  return modules
}

/**
 * Runs the unused-declaration analysis over the whole project ([AnalysisScope] of the project: production and test
 * sources), or over [modules] when the options name a scope (a use from outside them counts as `external`), and reads
 * every class, method and field of the graph in the scope before the context is cleaned up, which disposes it.
 * [GlobalInspectionContextImpl.performInspectionsWithProgress] runs on a pooled thread without a read lock, under an
 * indicator that is both a [com.intellij.openapi.wm.ex.ProgressIndicatorEx] and a
 * [ProgressIndicatorWithDelayedPresentation], as the headless `inspect` application runs it; calling it directly opens
 * no results view.
 */
private suspend fun analyze(project: Project, root: Path, modules: List<Module>?, testEntryPoints: Boolean): List<UnusedDeclaration> {
  val wrapper = GlobalInspectionToolWrapper(UnusedDeclarationInspection())
  val profile = createSimple("psiUnused", project, listOf(wrapper))
  // the profile's tool is the instance the context runs (a wrapper copy would start from the defaults again)
  val tool = profile.getInspectionTool(wrapper.shortName, project)!!.tool as UnusedDeclarationInspectionBase
  tool.isTestEntryPoints = testEntryPoints
  val context = (InspectionManager.getInstance(project) as InspectionManagerEx).createNewGlobalContext()
  context.setExternalProfile(profile)
  val scope = if (modules == null) AnalysisScope(project) else AnalysisScope(modules.toTypedArray())
  context.currentScope = scope
  val startedAt = System.currentTimeMillis()
  val declarations = ArrayList<UnusedDeclaration>()
  try {
    withContext(Dispatchers.IO) {
      ProgressManager.getInstance().runProcess({ context.performInspectionsWithProgress(scope, true, true) }, AnalysisIndicator())
      ProgressManager.getInstance().runProcess({
        ReadAction.run<Throwable> {
          context.refManager.iterate(DeclarationCollector(project, root, scope.takeIf { modules != null }, tool, declarations))
        }
      }, ProgressIndicatorBase())
    }
  }
  finally {
    context.cleanup()
  }
  declarations.sortWith(compareBy<UnusedDeclaration>({ it.file }, { it.offset }, { it.name }, { it.kind }))
  // a Kotlin property and its accessors share one source element
  val unique = declarations.distinctBy { it.key }
  log("analysis with test entry points $testEntryPoints: ${unique.size} declarations in ${System.currentTimeMillis() - startedAt} ms")
  return unique
}

private class AnalysisIndicator : ProgressIndicatorBase(), ProgressIndicatorWithDelayedPresentation {
  override fun setDelayInMillis(delayInMillis: Int) {}
}

/** Reads each class, method and field of the graph that has a name and lies in a file under [root] and in [scope]. */
private class DeclarationCollector(
  private val project: Project,
  private val root: Path,
  private val scope: AnalysisScope?,
  private val tool: UnusedDeclarationInspectionBase,
  private val out: MutableList<UnusedDeclaration>,
) : RefJavaVisitor() {
  private val fileIndex = ProjectFileIndex.getInstance(project)

  override fun visitClass(aClass: RefClass) {
    if (aClass.isAnonymous) return
    val psi = sourceOf(aClass) ?: return
    val javaClass = (aClass.uastElement?.javaPsi ?: aClass.psiElement) as? PsiClass
    val kind = when {
      javaClass == null -> "class"
      javaClass.isAnnotationType -> "annotation"
      javaClass.isInterface -> "interface"
      javaClass.isEnum -> "enum"
      javaClass.isRecord -> "record"
      else -> "class"
    }
    add(aClass, psi, kind, (aClass.owner as? RefClass)?.name)
  }

  override fun visitMethod(method: RefMethod) {
    if (method is RefImplicitConstructor) return
    val psi = sourceOf(method) ?: return
    val ownerClass = method.ownerClass
    val kind = when {
      method.isConstructor -> "constructor"
      ownerClass == null -> "function"
      else -> "method"
    }
    add(method, psi, kind, ownerClass?.name)
  }

  override fun visitField(field: RefField) {
    val psi = sourceOf(field) ?: return
    val ownerClass = field.ownerClass
    add(field, psi, if (psi.language.id == "JAVA") "field" else "property", ownerClass?.name)
  }

  private fun sourceOf(element: RefJavaElement): PsiElement? = element.uastElement?.sourcePsi ?: element.psiElement

  private fun add(element: RefJavaElement, psi: PsiElement, kind: String, owner: String?) {
    val file = psi.containingFile ?: return
    val virtualFile = file.virtualFile ?: return
    val path = runCatching { root.relativize(Path.of(virtualFile.path)) }.getOrNull()?.toString() ?: return
    if (path.startsWith("..") || !fileIndex.isInSourceContent(virtualFile) || scope?.contains(virtualFile) == false) return
    val name = (psi as? PsiNamedElement)?.name ?: return
    val nameElement = (psi as? PsiNameIdentifierOwner)?.nameIdentifier ?: psi
    val text = file.viewProvider.contents
    val offset = nameElement.textRange.startOffset
    val range = psi.textRange
    fun line(at: Int) = StringUtil.offsetToLineNumber(text, at) + 1
    out.add(UnusedDeclaration(
      file = path.replace('\\', '/'),
      offset = offset,
      line = line(offset),
      start = line(range.startOffset),
      end = line(maxOf(range.startOffset, range.endOffset - 1)),
      kind = kind,
      name = name,
      owner = owner,
      language = psi.language.id.lowercase(),
      test = fileIndex.isInTestSourceContent(virtualFile),
      dead = !element.isReachable,
      referenced = element.isReferenced,
      entry = if (element.isEntry) entryReason(element) else null,
    ))
  }

  /** Why [element] is an entry point, asked in the order of [UnusedDeclarationInspectionBase.isEntryPoint]. */
  private fun entryReason(element: RefJavaElement): String {
    val psi = element.uastElement?.javaPsi ?: element.psiElement ?: return "external"
    ImplicitUsageProvider.EP_NAME.extensionList.firstOrNull { it.isImplicitUsage(psi) }?.let { return "implicit:${it.javaClass.simpleName}" }
    if (psi is PsiModifierListOwner && EntryPointsManager.getInstance(project).isEntryPoint(psi)) return "annotation"
    tool.extensions.firstOrNull { it.isSelected && it.isEntryPoint(element, psi) }?.let { return "entry:${it.javaClass.simpleName}" }
    if (element is RefMethod && element.isAppMain) return "main"
    if (psi is PsiImplicitClass) return "implicit-class"
    if (element is RefClass && (element.isApplet || element.isServlet)) return if (element.isApplet) "applet" else "servlet"
    val uast = element.uastElement
    if (uast is UField && RefSerializationUtil.isSerializationImplicitlyUsedField(uast)) return "serialization"
    if (uast is UMethod && RefSerializationUtil.isSerializationMethod(uast, (element as? RefMethod)?.ownerClass)) return "serialization"
    return "external"
  }
}

private fun json(value: String): String {
  val out = StringBuilder(value.length + 2).append('"')
  for (c in value) {
    when {
      c == '"' -> out.append("\\\"")
      c == '\\' -> out.append("\\\\")
      c == '\n' -> out.append("\\n")
      c < ' ' -> out.append(String.format("\\u%04x", c.code))
      else -> out.append(c)
    }
  }
  return out.append('"').toString()
}

private fun log(message: String) {
  System.err.println("psi-unused: $message")
}
