// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
@file:OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.bufferedReader
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileVisitor
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries
import kotlin.io.path.visitFileTree
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.system.exitProcess

data class CopySpec(
  val source: String,
  val destination: String = source,
)

class BundleSource(private val copies: List<CopySpec>, private val sourceRootProvider: () -> Path) {
  init {
    require(copies.isNotEmpty()) { "Bundle source must define at least one copied path" }
  }

  fun copyInto(bundleTarget: Path) {
    val sourceRoot = sourceRootProvider()
    require(sourceRoot.exists()) { "Missing bundle source root: $sourceRoot" }
    for (copy in copies) {
      copyMapping(sourceRoot, bundleTarget, copy)
    }
  }
}

class CopyBuilder {
  private val copies = ArrayList<CopySpec>()

  fun copy(source: String, destination: String = source) {
    copies.add(CopySpec(normalizeCopyPath(source), normalizeCopyPath(destination)))
  }

  fun build(): List<CopySpec> = copies.toList()
}

val root: Path = Paths.get("").toAbsolutePath().normalize()
val checkoutRoot: Path = root.resolve("..").resolve("..").resolve("..").normalize()
val patchFile: Path = root.resolve("bundles.patch")
require(patchFile.isRegularFile()) { "Run this script from community/plugins/textmate" }

val tempDir: Path = Files.createTempDirectory("textmate-bundles")
val targetBundlesDir: Path = root.resolve("lib").resolve("bundles")
val bundlesDir: Path = tempDir.resolve("bundles")
val kotlinVscodeBundleDir: Path = checkoutRoot.resolve("language-server").resolve("community").resolve("kotlin-vscode")
val json: Json = Json {
  allowTrailingComma = true
  isLenient = true
  allowComments = true
}
val placeholderPattern = Regex("^%[^%]+%$")

var currentStage = "Starting"
var stageCounter = 0

fun logStage(title: String) {
  currentStage = title
  if (stageCounter > 0) {
    println()
  }

  val number = (++stageCounter).toString().padStart(2, '0')
  println("[$number] $title")
}

fun logDetail(message: String) {
  println("     $message")
}

fun logItem(message: String) {
  println("  - $message")
}

if (args.firstOrNull() == "--help") {
  println("Usage: ./loadVSCBundles.sh [--dry-run]")
  println("Refresh the bundled TextMate grammars under lib/bundles.")
  println()
  println("The script downloads upstream bundle sources, applies bundles.patch,")
  println("rewrites package metadata, and minifies JSON output.")
  println()
  println("Options:")
  println("  --dry-run   Prepare everything in staging but do not replace lib/bundles.")
  exitProcess(0)
}

val knownArgs = setOf("--dry-run")
require(args.all(knownArgs::contains)) { "Unexpected arguments: ${args.joinToString(" ")}" }
val dryRun = "--dry-run" in args

run {
  logStage("Preparing workspace")
  tempDir.deleteRecursively()
  tempDir.createDirectories()
  bundlesDir.createDirectories()
  logDetail("Temporary directory: $tempDir")
  logDetail("Staging bundles in:  $bundlesDir")
  logDetail("Target bundles dir:  $targetBundlesDir")

  try {
    logStage("Importing VS Code extension bundles")
    val vscodeBundleCount = loadVscodeExtensions()
    logDetail("Imported $vscodeBundleCount VS Code extension bundles")

    logStage("Importing curated bundles")
    var curatedBundleCount = 0

    fun importCuratedBundle(bundleName: String, vararg sources: BundleSource) {
      downloadBundle(bundleName, *sources)
      curatedBundleCount++
    }

    importCuratedBundle("bazel",
                        gitSource("https://github.com/bazel-contrib/vscode-bazel") {
                          copy("LICENSE")
                          copy("package.json")
                          copy("syntaxes/bazelrc.configuration.json")
                          copy("syntaxes/bazelrc.tmLanguage.yaml")
                          copy("syntaxes/starlark.configuration.json")
                          copy("syntaxes/starlark.tmLanguage.json")
                        })

    importCuratedBundle("bicep",
                        gitSource("https://github.com/Azure/bicep", "src") {
                          copy("vscode-bicep/LICENSE", "LICENSE")
                          copy("vscode-bicep/package.json", "package.json")
                          copy("vscode-bicep/syntaxes", "syntaxes")
                          copy("vscode-bicep/vscode-snippets", "vscode-snippets")
                          copy("textmate/language-configuration.json", "syntaxes/language-configuration.json")
                          copy("textmate/bicep.tmlanguage", "syntaxes/bicep.tmlanguage")
                        })

    importCuratedBundle("viml",
                        gitSource("https://github.com/AlexPl292/language-viml") {
                          copy("LICENSE.txt")
                          copy("package.json")
                          copy("grammars")
                        })

    importCuratedBundle("mdx",
                        gitSource("https://github.com/mdx-js/mdx-analyzer", "packages/vscode-mdx") {
                          copy("LICENSE")
                          copy("package.json")
                          copy("language-configuration.json")
                          copy("syntaxes")
                        })

    importCuratedBundle("twig",
                        gitSource("https://github.com/mblode/vscode-twig-language-2") {
                          copy("LICENSE.md")
                          copy("package.json")
                          copy("src/snippets")
                          copy("src/syntaxes")
                          copy("src/languages")
                        })

    importCuratedBundle("jsp",
                        gitSource("https://github.com/pthorsson/vscode-jsp") {
                          copy("LICENSE")
                          copy("package.json")
                          copy("jsp-configuration.json")
                          copy("syntaxes")
                        })

    importCuratedBundle("terraform",
                        gitSource("https://github.com/hashicorp/vscode-terraform") {
                          copy("LICENSE")
                          copy("package.json")
                          copy("language-configuration.json")
                        },
                        gitSource("https://github.com/hashicorp/syntax") {
                          copy("syntaxes/terraform.tmGrammar.json", "syntaxes/terraform.tmGrammar.json")
                          copy("syntaxes/hcl.tmGrammar.json", "syntaxes/hcl.tmGrammar.json")
                        })

    importCuratedBundle("adoc",
                        gitSource("https://github.com/asciidoctor/asciidoctor-vscode") {
                          copy("LICENSE")
                          copy("package.json")
                          copy("package.nls.json")
                          copy("asciidoc-language-configuration.json")
                          copy("syntaxes")
                          copy("snippets")
                        })

    importCuratedBundle("hcl",
                        gitSource("https://github.com/hashicorp/vscode-hcl") {
                          copy("LICENSE")
                          copy("package.json")
                          copy("language-configuration.json")
                        },
                        gitSource("https://github.com/hashicorp/syntax") {
                          copy("syntaxes/hcl.tmGrammar.json", "syntaxes/hcl.tmGrammar.json")
                        })

    importCuratedBundle("cmake",
                        gitSource("https://github.com/twxs/vs.language.cmake") {
                          copy("LICENSE")
                          copy("package.json")
                          copy("syntaxes")
                        })

    importCuratedBundle("kconfig",
                        gitSource("https://github.com/trond-snekvik/vscode-kconfig/") {
                          copy("LICENSE")
                          copy("package.json")
                          copy("language-configuration.json")
                          copy("syntaxes")
                        })

    importCuratedBundle("erlang",
                        gitSource("https://github.com/erlang-ls/vscode") {
                          copy("language-configuration.json")
                          copy("package.json")
                          copy("LICENSE.md")
                        },
                        gitSource("https://github.com/erlang-ls/grammar") {
                          copy("Erlang.plist", "grammar/Erlang.plist")
                        })

    importCuratedBundle("kotlin",
                        localSource(kotlinVscodeBundleDir) {
                          copy("LICENSE")
                          copy("package.json")
                          copy("language-configuration.json")
                          copy("syntaxes")
                        })

    logDetail("Imported $curatedBundleCount curated bundles")

    logStage("Cleaning bundle directory")
    cleanupBundlesDir()

    logStage("Applying local patch")
    runCommand(
      bundlesDir,
      listOf("git", "apply", "--allow-empty", patchFile.toString()),
    )
    logDetail("Processed ${patchFile.fileName}")

    logStage("Rewriting package metadata")
    val packageJsonCount = cleanupPackageJsons()
    logDetail("Rewrote $packageJsonCount package.json files")

    logStage("Minifying JSON files")
    val jsonFileCount = minifyJsonFiles()
    logDetail("Minified $jsonFileCount JSON files")

    if (dryRun) {
      logStage("Dry run complete")
      logDetail("Prepared ${vscodeBundleCount + curatedBundleCount} bundle directories")
      logDetail("Target bundles directory was not modified. Staged result is available at $tempDir")
    }
    else {
      logStage("Replacing target bundles directory")
      replaceTargetBundlesDirectory()
      logDetail("Activated freshly prepared bundles")

      logStage("Done")
      logDetail("Refreshed ${vscodeBundleCount + curatedBundleCount} bundle directories")
      tempDir.deleteRecursively()
    }
  }
  catch (t: Throwable) {
    println()
    println("[!!] Failed while $currentStage. Preliminary result is available at $tempDir")
    t.message?.let(::logDetail)
    throw t
  }
}

fun loadVscodeExtensions(): Int {
  val vscodeCheckoutDir = tempDir.resolve(inferGitSourceName("https://github.com/Microsoft/vscode"))
  cloneLatestSparse(
    repoUrl = "https://github.com/Microsoft/vscode",
    destination = vscodeCheckoutDir,
    sparsePaths = listOf("/extensions/"),
  )

  var importedBundleCount = 0
  val extensionsDir = vscodeCheckoutDir.resolve("extensions")
  Files.newDirectoryStream(extensionsDir).use { directories ->
    for (directory in directories) {
      if (!directory.resolve("syntaxes").isDirectory()) {
        continue
      }

      logItem(directory.name)
      val target = bundlesDir.resolve(directory.name)
      directory.copyToRecursively(target, overwrite = false, followLinks = true)
      target.resolve("test").deleteRecursively()
      target.resolve("build").deleteRecursively()
      target.resolve("src").deleteRecursively()
      target.resolve("resources").deleteRecursively()
      target.resolve("yarn.lock").deleteRecursively()
      target.resolve("cgmanifest.json").deleteRecursively()
      importedBundleCount++
    }
  }

  return importedBundleCount
}

fun downloadBundle(bundleName: String, vararg sources: BundleSource) {
  require(sources.isNotEmpty()) { "Bundle $bundleName must define at least one source" }
  logItem(bundleName)
  val bundleTarget = bundlesDir.resolve(bundleName)
  bundleTarget.createDirectories()
  for (source in sources) {
    source.copyInto(bundleTarget)
  }
}

fun copyMapping(sourceRoot: Path, bundleTarget: Path, mapping: CopySpec) {
  val source = sourceRoot.resolve(mapping.source).normalize()
  if (!source.exists()) {
    error("Missing required path: $source")
  }

  val target = bundleTarget.resolve(mapping.destination).createParentDirectories()
  source.copyToRecursively(target, overwrite = false, followLinks = true)
}

fun gitSource(repoUrl: String, sourceRoot: String = ".", block: CopyBuilder.() -> Unit): BundleSource {
  val copies = CopyBuilder().apply(block).build()
  return BundleSource(copies) {
    val destination = tempDir.resolve(inferGitSourceName(repoUrl))
    val preparedSourceRoot = destination.resolve(sourceRoot).normalize()
    val requiresCheckout = !preparedSourceRoot.exists() || copies.any { copy ->
      !preparedSourceRoot.resolve(copy.source).normalize().exists()
    }
    if (requiresCheckout) {
      destination.deleteRecursively()
      cloneLatestSparse(repoUrl, destination, sparsePathsFor(sourceRoot, copies))
    }
    destination.resolve(sourceRoot).normalize()
  }
}

fun inferGitSourceName(repoUrl: String): String {
  val normalizedUrl = repoUrl.trimEnd('/').removeSuffix(".git")
  return normalizedUrl.substringAfterLast("/")
}

fun localSource(directory: Path, sourceRoot: String = ".", block: CopyBuilder.() -> Unit): BundleSource {
  val copies = CopyBuilder().apply(block).build()
  return BundleSource(copies) {
    directory.resolve(sourceRoot).normalize()
  }
}

fun sparsePathsFor(sourceRoot: String, copies: List<CopySpec>): List<String> {
  return buildSet {
    for (copy in copies) {
      val sourcePath = fullSourcePath(sourceRoot, copy.source)
      add("/$sourcePath")
      add("/$sourcePath/")
    }
  }.toList()
}

fun fullSourcePath(sourceRoot: String, relativePath: String): String {
  val normalizedSourceRoot = normalizeSourceRoot(sourceRoot)
  return if (normalizedSourceRoot.isEmpty()) relativePath else "$normalizedSourceRoot/$relativePath"
}

fun normalizeSourceRoot(sourceRoot: String): String {
  return sourceRoot.removePrefix("./").trim('/').takeUnless { it == "." } ?: ""
}

fun normalizeCopyPath(path: String): String {
  val normalized = path.removePrefix("./").replace('\\', '/').trim('/')
  require(normalized.isNotEmpty()) { "Copy path must not be blank" }
  return normalized
}

fun cloneLatestSparse(repoUrl: String, destination: Path, sparsePaths: List<String>) {
  val sparseCloneSucceeded = runCommand(
    root,
    listOf("git",
           "clone",
           "--quiet",
           "--depth",
           "1",
           "--single-branch",
           "--no-tags",
           "--filter=blob:none",
           "--sparse",
           repoUrl,
           destination.toString()),
    failOnError = false,
  ) && runCommand(
    root,
    listOf("git", "-C", destination.toString(), "sparse-checkout", "set", "--no-cone", *sparsePaths.toTypedArray()),
    failOnError = false,
  )

  if (sparseCloneSucceeded) {
    return
  }

  destination.deleteRecursively()
  logDetail("Sparse checkout unavailable for ${destination.fileName}; retrying with a full clone")
  runCommand(
    root,
    listOf("git", "clone", "--quiet", "--depth", "1", "--single-branch", "--no-tags", repoUrl, destination.toString()),
  )
}

fun replaceTargetBundlesDirectory() {
  targetBundlesDir.deleteRecursively()
  bundlesDir.moveTo(targetBundlesDir)
}

fun runCommand(workingDir: Path, command: List<String>, failOnError: Boolean = true): Boolean {
  val processBuilder = ProcessBuilder(command)
    .directory(workingDir.toFile())
    .inheritIO()

  if (command.take(2) == listOf("git", "clone")) {
    processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
  }

  val exitCode = processBuilder.start().waitFor()

  if (exitCode == 0) {
    return true
  }

  if (failOnError) {
    error("Command failed with exit code $exitCode: ${command.joinToString(" ")}")
  }

  return false
}

fun cleanupBundlesDir() {
  val extensionsToClean = setOf("js", "ts", "mts", "png")
  bundlesDir.visitFileTree(fileVisitor {
    onPreVisitDirectory { directory, _ ->
      if (directory.startsWith(".")) {
        directory.deleteRecursively()
        FileVisitResult.SKIP_SUBTREE
      }
      else {
        FileVisitResult.CONTINUE
      }
    }

    onVisitFile { file, _ ->
      if (file.name.startsWith(".") ||
          file.name.startsWith("tsconfig.") ||
          file.name.equals("package-lock.json", ignoreCase = true) ||
          file.name.equals("README.md", ignoreCase = true) ||
          file.extension in extensionsToClean) {
        file.deleteExisting()
      }
      FileVisitResult.CONTINUE
    }
  })
}

fun cleanupPackageJsons(): Int {
  var rewrittenPackageCount = 0
  bundlesDir.useDirectoryEntries { bundleDirectories ->
    for (bundleDirectory in bundleDirectories) {
      val packageJson = bundleDirectory.resolve("package.json")
      if (packageJson.isRegularFile()) {
        val packageNls = bundleDirectory.resolve("package.nls.json").takeIf { it.isRegularFile() }
        packageJson.writeText(json.encodeToString(JsonElement.serializer(), cleanupPackageJson(bundleDirectory.name, packageJson, packageNls)))
        packageNls?.deleteIfExists()
        rewrittenPackageCount++
      }
    }
  }

  return rewrittenPackageCount
}

fun cleanupPackageJson(bundleName: String, packageJson: Path, packageNls: Path?): JsonObject {
  val packageObject = readJson(packageJson).jsonObject
  val cleanedPackage = if (packageNls != null) {
    localizeStrings(packageObject, readJson(packageNls).jsonObject).jsonObject
  }
  else {
    packageObject
  }
  val rewrittenPackage = rewriteKnownPackagePaths(bundleName, cleanedPackage)

  val selectedFields = listOf("name", "displayName", "version", "description", "license", "contributes")
  return buildJsonObject {
    for (field in selectedFields) {
      rewrittenPackage[field]?.let { put(field, it) }
    }
  }
}

fun rewriteKnownPackagePaths(bundleName: String, packageObject: JsonObject): JsonObject {
  return when (bundleName) {
    "bazel" -> replaceJsonString(packageObject, "./syntaxes/bazelrc.tmLanguage.json", "./syntaxes/bazelrc.tmLanguage.yaml").jsonObject
    else -> packageObject
  }
}

fun replaceJsonString(element: JsonElement, oldValue: String, newValue: String): JsonElement {
  return when (element) {
    is JsonObject -> buildJsonObject {
      for ((key, value) in element) {
        put(key, replaceJsonString(value, oldValue, newValue))
      }
    }
    is JsonArray -> buildJsonArray {
      for (value in element) {
        add(replaceJsonString(value, oldValue, newValue))
      }
    }
    is JsonPrimitive if element.isString && element.content == oldValue -> JsonPrimitive(newValue)
    else -> element
  }
}

fun localizeStrings(element: JsonElement, packageNls: JsonObject): JsonElement {
  return when (element) {
    is JsonObject -> buildJsonObject {
      for ((key, value) in element) {
        put(key, localizeStrings(value, packageNls))
      }
    }
    is JsonArray -> buildJsonArray {
      for (value in element) {
        add(localizeStrings(value, packageNls))
      }
    }
    is JsonPrimitive if element.isString -> {
      val stringValue = element.content
      if (!placeholderPattern.matches(stringValue)) {
        JsonPrimitive(stringValue)
      }
      else {
        val key = stringValue.removePrefix("%").removeSuffix("%")
        packageNls[key] ?: JsonPrimitive(stringValue)
      }
    }
    else -> element
  }
}

fun minifyJsonFiles(): Int {
  var minifiedFilesCount = 0
  bundlesDir.walk().filter { file -> file.extension == "json" }.forEach { file ->
    file.writeText(json.encodeToString(JsonElement.serializer(), readJson(file)))
    minifiedFilesCount += 1
  }
  return minifiedFilesCount
}

fun readJson(path: Path): JsonElement = path.bufferedReader().use { reader ->
  json.parseToJsonElement(reader.readText())
}
