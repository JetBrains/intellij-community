// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.lang.properties.PropertiesImplUtil
import com.intellij.lang.properties.ResourceBundleManager
import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SystemProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolute
import kotlin.system.exitProcess

private const val COMMAND = "psiBundleRefs"

private const val USAGE = """Usage: $COMMAND --project=<dir> --jdk=<JDK home> --output=<bundles.json> [--scope=<dir>,...]
  --project=<dir>         the project to open; a directory with .idea opens without import
  --jdk=<JDK home>        a full JDK with lib/src.zip; it is registered under every JDK name that the project asks for
  --output=<bundles.json> every key of the message bundles with the references the IDE resolves to it
  --scope=<dir>,...       only the bundles under these directories of the project (the references are searched in the
                          whole project)"""

private val IMPLICIT_USAGE = ExtensionPointName<ImplicitPropertyUsageProvider>("com.intellij.properties.implicitPropertyUsageProvider")

private class BundleOptions(@JvmField val project: Path, @JvmField val jdk: Path, @JvmField val output: Path, @JvmField val scope: List<Path>) {
  companion object {
    fun parse(args: List<String>): BundleOptions {
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
      return BundleOptions(project, jdk, output, scope)
    }
  }
}

/**
 * Opens a project headless and writes, for every key of its message bundles, what the IDE links to the key: the
 * oracle of accio's bundle keys (`references --at <bundle>:<line>`, JetBrains/accio#542).
 *
 * A message bundle is a `.properties` file named `…Bundle.properties` or under a `messages/` directory, the rule of
 * accio's `extract::bundle::is_bundle_path`. Per bundle the output names its full name as the IDE reads it
 * ([ResourceBundleManager.getFullName]: the package of its resource root and its base name, the locale suffix
 * stripped) and its module; per key:
 * - `refs`: every reference [ReferencesSearch] finds in the project, with whether it is soft ([PsiReference.isSoft]: a
 *   literal that only spells the key, java-i18n's reference outside a `@PropertyKey` position) and its class, which
 *   tells the provider (java-i18n's code literals, DevKit's descriptor attributes, UI Designer's forms);
 * - `targets`: what the key's own references resolve to (DevKit's `action.<id>.text` to the action, a tool window,
 *   a plugin id, an advanced setting), at the element a navigation goes to: a DOM target's attribute value, not the
 *   start of its file;
 * - `implicit`: whether an [ImplicitPropertyUsageProvider] counts the key as used, as `UnusedProperty` asks first.
 *
 * Lines are 1-based, columns 0-based in UTF-16 units, as accio's JSON counts them. Bundles, keys and references are
 * sorted, so two runs on one revision write the same bytes. The starter never saves the project or the application
 * settings. Run it with dedicated `idea.config.path` and `idea.system.path`, and on a copy of the project. The exit code
 * is 0 after the output is written, 1 on a failure and 2 on a usage error.
 */
internal class PsiBundleRefsStarter : ModernApplicationStarter() {
  override val isHeadless: Boolean = true

  override fun premain(args: List<String>) {
    System.setProperty("ide.browser.jcef.enabled", false.toString())
    System.setProperty("vcs.log.index.enable", false.toString())
    System.setProperty("ide.slow.operations.assertion", false.toString())
    SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", true.toString())
  }

  override suspend fun start(args: List<String>) {
    val options = try {
      BundleOptions.parse(args.drop(1))
    }
    catch (e: IllegalArgumentException) {
      System.err.println("Error: ${e.message}")
      System.err.println(USAGE)
      exitProcess(2)
    }
    ApplicationManagerEx.getApplicationEx().setSaveAllowed(false)
    val exitCode = try {
      exportBundleRefs(options)
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

/** A place in a file of the project: its path relative to the root, its 1-based line and 0-based UTF-16 column. */
private data class Place(@JvmField val file: String, @JvmField val line: Int, @JvmField val column: Int) : Comparable<Place> {
  override fun compareTo(other: Place): Int = compareValuesBy(this, other, { it.file }, { it.line }, { it.column })
}

private class Ref(@JvmField val at: Place, @JvmField val soft: Boolean, @JvmField val reference: String, @JvmField val language: String)

private class Target(@JvmField val at: Place, @JvmField val reference: String)

private class Key(
  @JvmField val key: String,
  @JvmField val line: Int,
  @JvmField val refs: List<Ref>,
  @JvmField val targets: List<Target>,
  @JvmField val implicit: Boolean,
)

private class Bundle(@JvmField val file: String, @JvmField val name: String?, @JvmField val module: String?, @JvmField val keys: List<Key>)

private suspend fun exportBundleRefs(options: BundleOptions) {
  val startedAt = System.currentTimeMillis()
  val project = openReadyProject(options.project, options.jdk, ::log)
  log("project is ready after ${System.currentTimeMillis() - startedAt} ms")
  val files = smartReadAction(project) {
    FileTypeIndex.getFiles(PropertiesFileType.INSTANCE, GlobalSearchScope.projectScope(project))
      .mapNotNull { file -> relative(options.project, file)?.let { it to file } }
      .filter { (path, _) -> isBundlePath(path) && (options.scope.isEmpty() || options.scope.any { options.project.resolve(path).startsWith(it) }) }
      .sortedBy { it.first }
  }
  log("${files.size} bundles")
  // Each key is searched in a read action of its own, many at once: a search reads the word index and resolves each
  // candidate literal (a call's parameter, an annotation's bundle), which needs no write and takes about a second.
  val parallelism = SystemProperties.getIntProperty("psi.bundle.refs.parallelism", maxOf(1, Runtime.getRuntime().availableProcessors() - 4))
  val keyDispatcher = Dispatchers.Default.limitedParallelism(parallelism)
  val done = AtomicInteger()
  val shapes = files.mapNotNull { (path, file) -> smartReadAction(project) { shapeOf(project, path, file) } }
  val keys: List<List<Key>> = coroutineScope {
    shapes.map { shape ->
      shape.properties.map { property ->
        async(keyDispatcher) {
          val key = smartReadAction(project) { keyOf(project, options.project, shape.psi, property) }
          val n = done.incrementAndGet()
          if (n % 1000 == 0) log("$n keys")
          key
        }
      }
    }.map { bundle -> bundle.awaitAll().filterNotNull().sortedWith(compareBy<Key>({ it.line }, { it.key })) }
  }
  val bundles = shapes.zip(keys) { shape, k -> Bundle(shape.path, shape.name, shape.module, k) }
  val references = bundles.sumOf { b -> b.keys.sumOf { it.refs.size } }
  write(options.output, bundles)
  log("wrote ${options.output}: ${bundles.sumOf { it.keys.size }} keys, $references references; total ${System.currentTimeMillis() - startedAt} ms")
}

/** A bundle before its keys are searched: its file, full name, module and properties. */
private class BundleShape(@JvmField val path: String, @JvmField val psi: PsiFile, @JvmField val name: String?, @JvmField val module: String?, @JvmField val properties: List<SmartPsiElementPointer<Property>>)

/** The properties of one bundle; null for a file the IDE reads as no properties file. */
private fun shapeOf(project: Project, path: String, file: VirtualFile): BundleShape? {
  val psi = PsiManager.getInstance(project).findFile(file) ?: return null
  val properties: PropertiesFile = PropertiesImplUtil.getPropertiesFile(psi) ?: return null
  val pointers = SmartPointerManager.getInstance(project)
  val keys = properties.properties.mapNotNull { it.psiElement as? Property }.map { pointers.createSmartPsiElementPointer(it) }
  return BundleShape(path, psi, ResourceBundleManager.getInstance(project).getFullName(properties), ModuleUtilCore.findModuleForFile(file, project)?.name, keys)
}

/** One key with what the IDE links to it. */
private fun keyOf(project: Project, root: Path, psi: PsiFile, pointer: SmartPsiElementPointer<Property>): Key? {
  val property = pointer.element ?: return null
  val key = property.unescapedKey ?: return null
  val scope = GlobalSearchScope.projectScope(project)
  val refs = ReferencesSearch.search(property, scope).findAll().mapNotNull { ref ->
    val at = placeOf(root, ref.element.containingFile, ref.element.textRange.startOffset + ref.rangeInElement.startOffset) ?: return@mapNotNull null
    Ref(at, ref.isSoft, ref.javaClass.name, ref.element.containingFile.language.id)
  }.sortedWith(compareBy<Ref>({ it.at }, { it.reference }))
  val keyElement = PsiTreeUtil.getChildOfType(property, PropertyKeyImpl::class.java)
  val targets = keyElement?.references.orEmpty().flatMap { ref ->
    resolved(ref).mapNotNull { target -> target.navigationElement.let { placeOf(root, it.containingFile, it.textOffset) }?.let { Target(it, ref.javaClass.name) } }
  }.sortedWith(compareBy<Target>({ it.at }, { it.reference }))
  val implicit = IMPLICIT_USAGE.extensionList.any { it.isUsed(property) }
  return Key(key, placeOf(root, psi, property.textOffset)?.line ?: 0, refs, targets, implicit)
}

private fun resolved(ref: PsiReference): List<PsiElement> =
  if (ref is PsiPolyVariantReference) ref.multiResolve(false).mapNotNull { it.element } else listOfNotNull(ref.resolve())

private fun placeOf(root: Path, file: PsiFile?, offset: Int): Place? {
  val virtualFile = file?.virtualFile ?: return null
  val path = relative(root, virtualFile) ?: return null
  val text = file.viewProvider.contents
  var line = 1
  var lineStart = 0
  for (i in 0 until minOf(offset, text.length)) {
    if (text[i] == '\n') {
      line++
      lineStart = i + 1
    }
  }
  return Place(path, line, offset - lineStart)
}

private fun relative(root: Path, file: VirtualFile): String? {
  val path = file.fileSystem.getNioPath(file) ?: return null
  return if (path.startsWith(root)) root.relativize(path).toString().replace('\\', '/') else null
}

/** accio's `extract::bundle::is_bundle_path`. */
private fun isBundlePath(path: String): Boolean =
  path.endsWith("Bundle.properties") || (path.endsWith(".properties") && path.contains("/messages/"))

private fun write(output: Path, bundles: List<Bundle>) {
  Files.createDirectories(output.parent)
  Files.newBufferedWriter(output).use { out ->
    out.write("{\n")
    out.write("  \"tool\": \"psi-bundle-refs\",\n")
    out.write("  \"version\": ${json(ApplicationInfo.getInstance().build.asString())},\n")
    out.write("  \"bundles\": [\n")
    bundles.forEachIndexed { b, bundle ->
      out.write("    {\"file\": ${json(bundle.file)}, \"bundle\": ${bundle.name?.let(::json) ?: "null"}, \"module\": ${bundle.module?.let(::json) ?: "null"}, \"keys\": [\n")
      bundle.keys.forEachIndexed { k, key ->
        val refs = key.refs.joinToString(", ") {
          "{\"file\": ${json(it.at.file)}, \"line\": ${it.at.line}, \"column\": ${it.at.column}, \"soft\": ${it.soft}, " +
          "\"reference\": ${json(it.reference)}, \"language\": ${json(it.language)}}"
        }
        val targets = key.targets.joinToString(", ") {
          "{\"file\": ${json(it.at.file)}, \"line\": ${it.at.line}, \"reference\": ${json(it.reference)}}"
        }
        out.write("      {\"key\": ${json(key.key)}, \"line\": ${key.line}, \"implicit\": ${key.implicit}, \"refs\": [$refs], \"targets\": [$targets]}")
        out.write(if (k < bundle.keys.size - 1) ",\n" else "\n")
      }
      out.write(if (b < bundles.size - 1) "    ]},\n" else "    ]}\n")
    }
    out.write("  ]\n}\n")
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
  System.err.println("psi-bundle-refs: $message")
}
