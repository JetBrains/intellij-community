// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.allure.annotations.scanner

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.stream.Collectors

private val PACKAGE_LINE = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
private val QUALIFIED_ANNOTATION = Regex("""^@(\w+)\.(\w+)""")
private val TAG_ANNOTATION = Regex("""^@(?:org\.junit\.jupiter\.api\.)?Tag\(\s*"([^"]+)"\s*\)""")
private val CLASS_DECLARATION = Regex("""\bclass\s+(\w+)\b""")
private val TEST_NAME_SUFFIX = Regex(""".*(Test|Tests|TestCase|TestSuite)$""")

private val ANNOTATION_QUALIFIERS = setOf(
  "Components", "Subsystems", "Layers",
  "Owners", "Features", "Stories",
)

private const val TAG_BUFFER_KEY = "Tags"

private data class Variant(
  val name: String? = null,
  val status: String? = null,
  val durationMs: Long? = null,
  val errorMessage: String? = null,
)

private data class LastRun(
  val status: String? = null,
  val durationMs: Long? = null,
  val errorMessage: String? = null,
  val source: String? = null,
  val runAt: String? = null,
  val variants: List<Variant> = emptyList(),
)

private data class TestRecord(
  val file: String,
  val fqn: String,
  val subsystems: List<String>,
  val components: List<String>,
  val layers: List<String>,
  val owners: List<String>,
  val features: List<String>,
  val stories: List<String>,
  val tags: List<String>,
  val lastRun: LastRun = LastRun(),
)

private data class UnannotatedRecord(
  val file: String,
  val fqn: String,
)

private data class Report(
  val generatedAt: String,
  val repoCommit: String?,
  val tests: List<TestRecord>,
  val unannotated: List<UnannotatedRecord>,
)

private sealed class ScanResult {
  data class Annotated(val record: TestRecord) : ScanResult()
  data class Unannotated(val file: String, val fqn: String) : ScanResult()
  data object Skip : ScanResult()
}

private class Args(
  val sourceRoots: List<Path>,
  val output: Path,
  val repoRoot: Path,
)

private fun parseArgs(args: Array<String>): Args {
  val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
  var sourceRoots: List<String> = listOf("python/junit5Tests/tests", "python/testSrc")
  var output = "build/annotation-report.json"
  var repoRoot: Path = cwd

  for (arg in args) {
    when {
      arg.startsWith("--source-roots=") -> sourceRoots = arg.substringAfter("=").split(",").map { it.trim() }.filter { it.isNotEmpty() }
      arg.startsWith("--output=") -> output = arg.substringAfter("=")
      arg.startsWith("--repo-root=") -> repoRoot = Paths.get(arg.substringAfter("=")).toAbsolutePath().normalize()
      arg == "--help" || arg == "-h" -> {
        printUsage()
        kotlin.system.exitProcess(0)
      }
      else -> error("Unknown argument: $arg (use --help for usage)")
    }
  }

  return Args(
    sourceRoots = sourceRoots.map { repoRoot.resolve(it).normalize() },
    output = repoRoot.resolve(output).normalize(),
    repoRoot = repoRoot,
  )
}

private fun printUsage() {
  println("""
    |Usage: AnnotationScanner [--source-roots=<comma-separated paths>] [--output=<path>] [--repo-root=<path>]
    |
    |Defaults (relative to repo root):
    |  --source-roots=python/junit5Tests/tests,python/testSrc
    |  --output=build/annotation-report.json
    |  --repo-root=<current working directory>
    |
    |Scans .kt and .java files for class-level annotations from
    |com.intellij.ide.starter.extended.allure (Components, Subsystems, Layers, ...)
    |plus JUnit 5 @Tag("...") annotations, and emits a JSON report.
  """.trimMargin())
}

fun main(args: Array<String>) {
  val parsed = parseArgs(args)

  val records = mutableListOf<TestRecord>()
  val unannotated = mutableListOf<UnannotatedRecord>()
  for (root in parsed.sourceRoots) {
    if (!Files.exists(root)) {
      System.err.println("warn: source root does not exist: $root")
      continue
    }
    val files = Files.walk(root).use { stream ->
      stream
        .filter {
          if (!Files.isRegularFile(it)) return@filter false
          val name = it.toString()
          name.endsWith(".kt") || name.endsWith(".java")
        }
        .collect(Collectors.toList())
    }
    for (file in files) {
      when (val r = scanFile(file, parsed.repoRoot)) {
        is ScanResult.Annotated -> records.add(r.record)
        is ScanResult.Unannotated -> unannotated.add(UnannotatedRecord(r.file, r.fqn))
        ScanResult.Skip -> {}
      }
    }
  }

  val report = Report(
    generatedAt = Instant.now().toString(),
    repoCommit = currentGitHead(parsed.repoRoot),
    tests = records.sortedBy { it.fqn },
    unannotated = unannotated.sortedBy { it.fqn },
  )

  Files.createDirectories(parsed.output.parent)
  Files.writeString(
    parsed.output,
    GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create().toJson(report),
  )

  println(
    "Scanned ${parsed.sourceRoots.size} source root(s); " +
    "wrote ${records.size} annotated and ${unannotated.size} unannotated record(s) " +
    "to ${parsed.output}"
  )
}

private fun scanFile(file: Path, repoRoot: Path): ScanResult {
  val text = Files.readString(file)
  val pkg = PACKAGE_LINE.find(text)?.groupValues?.get(1).orEmpty()

  val buffer = mutableListOf<Pair<String, String>>()
  var className: String? = null
  var firstClassSeen: String? = null

  val lines = text.lines()
  var i = 0
  while (i < lines.size) {
    val rawLine = lines[i]
    val line = rawLine.trim()
    i++

    if (line.isEmpty()) continue
    if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) continue

    if (line.startsWith("@")) {
      // Track only qualified label-style annotations we care about; ignore others
      // (e.g. @Test, @ParameterizedClass) without clearing the buffer.
      QUALIFIED_ANNOTATION.find(line)?.let { m ->
        val qual = m.groupValues[1]
        val name = m.groupValues[2]
        if (qual in ANNOTATION_QUALIFIERS) {
          buffer.add(qual to name)
        }
      }
      TAG_ANNOTATION.find(line)?.let { m ->
        buffer.add(TAG_BUFFER_KEY to m.groupValues[1])
      }
      // Skip continuation lines for multi-line annotation arguments.
      var depth = parenBalance(line)
      while (depth > 0 && i < lines.size) {
        depth += parenBalance(lines[i])
        i++
      }
      // Annotation may be on the same line as the class declaration.
      val classMatch = CLASS_DECLARATION.find(line)
      if (classMatch != null) {
        if (firstClassSeen == null) firstClassSeen = classMatch.groupValues[1]
        if (buffer.isNotEmpty()) {
          className = classMatch.groupValues[1]
          break
        }
      }
      continue
    }

    val classMatch = CLASS_DECLARATION.find(line)
    if (classMatch != null) {
      if (firstClassSeen == null) firstClassSeen = classMatch.groupValues[1]
      if (buffer.isNotEmpty()) {
        className = classMatch.groupValues[1]
        break
      }
      // Helper class with no preceding annotations — keep scanning.
      continue
    }

    // Other code line (function, property, etc.) — annotations weren't on a class.
    buffer.clear()
  }

  val relPath = repoRoot.relativize(file.toAbsolutePath()).toString().replace('\\', '/')

  val resolvedClassName = className
  if (resolvedClassName != null && buffer.isNotEmpty()) {
    return ScanResult.Annotated(
      TestRecord(
        file = relPath,
        fqn = if (pkg.isNotEmpty()) "$pkg.$resolvedClassName" else resolvedClassName,
        subsystems = buffer.filter { it.first == "Subsystems" }.map { it.second },
        components = buffer.filter { it.first == "Components" }.map { it.second },
        layers = buffer.filter { it.first == "Layers" }.map { it.second },
        owners = buffer.filter { it.first == "Owners" }.map { it.second },
        features = buffer.filter { it.first == "Features" }.map { it.second },
        stories = buffer.filter { it.first == "Stories" }.map { it.second },
        tags = buffer.filter { it.first == TAG_BUFFER_KEY }.map { it.second },
      )
    )
  }

  // No labels attached to any class in this file. Surface the file in
  // the unannotated list only when the first top-level class name looks
  // test-y — otherwise it's almost certainly a helper utility and we
  // don't want to flood the list.
  val firstClass = firstClassSeen
  if (firstClass != null && TEST_NAME_SUFFIX.matches(firstClass)) {
    return ScanResult.Unannotated(
      file = relPath,
      fqn = if (pkg.isNotEmpty()) "$pkg.$firstClass" else firstClass,
    )
  }
  return ScanResult.Skip
}

private fun parenBalance(line: String): Int {
  var depth = 0
  var inString = false
  var prev = ' '
  for (ch in line) {
    if (inString) {
      if (ch == '"' && prev != '\\') inString = false
    }
    else {
      when (ch) {
        '"' -> inString = true
        '(' -> depth++
        ')' -> depth--
      }
    }
    prev = ch
  }
  return depth
}

@Suppress("IO_FILE_USAGE")
private fun currentGitHead(repoRoot: Path): String? {
  return try {
    val process = ProcessBuilder("git", "rev-parse", "HEAD")
      .directory(repoRoot.toFile())
      .redirectErrorStream(true)
      .start()
    val finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
    if (!finished) {
      process.destroyForcibly()
      return null
    }
    if (process.exitValue() != 0) return null
    process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
  }
  catch (_: Exception) {
    null
  }
}
