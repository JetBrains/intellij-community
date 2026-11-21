// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.parser

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.BlockSupportImpl
import com.intellij.psi.impl.ChangedPsiRangeUtil
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YamlBlockCollectionType
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The settings of a long bug hunt. The default values give the short run of the normal test suite.
 *
 * - `yaml.reparse.hunt.wide=true` adds all the YAML files of the test data to the texts, and more edits to the brute force.
 * - `yaml.reparse.hunt.minutes=N` makes the fuzz test run new random sequences for N minutes.
 * - `yaml.reparse.hunt.seed=N` sets the first seed of the fuzz test. Without it, a hunt takes a random seed.
 */
internal object YamlReparseHunt {
  val wide: Boolean = System.getProperty("yaml.reparse.hunt.wide").toBoolean()
  val minutes: Int = Integer.getInteger("yaml.reparse.hunt.minutes", 0)
  val seed: Long? = System.getProperty("yaml.reparse.hunt.seed")?.toLong()
}

/** The kind of the reparse of one change. */
internal enum class YamlReparseKind { NONE, FULL, LEAF, BLOCK, OTHER }

/** One change of a text: the range of the old text and the text that replaces it. */
internal data class YamlChange(val range: TextRange, val replacement: String) {
  fun apply(text: String): String = range.replace(text, replacement)
  override fun toString(): String = "$range to '${escapeYaml(replacement)}'"
}

/**
 * The texts of the reparse tests: the YAML files of the parser and the lexer test data, and [extra] texts.
 * With [YamlReparseHunt.wide], also all the other YAML files of the test data, without the test markers.
 */
internal fun yamlReparseTexts(extra: List<String> = emptyList(), maxLength: Int = 6000): List<Pair<String, String>> {
  val root = File(PathManagerEx.getCommunityHomePath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml")
  val narrow = listOf("parser/data", "lexer/data").flatMap { dir ->
    val files = File(root, dir).listFiles { f -> f.extension == "yml" }.orEmpty()
    check(files.isNotEmpty()) { "no YAML test data in ${File(root, dir)}" }
    files.sortedBy { it.name }
  }
  val wide = if (YamlReparseHunt.wide) root.walkTopDown().filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }.sortedBy { it.path }.toList() else emptyList()
  val seen = HashSet<String>()
  return ((narrow + wide).map { it.relativeTo(root).path to MARKER.replace(it.readText().replace("\r\n", "\n"), "") } +
          extra.mapIndexed { i, s -> "extra$i" to s })
    .filter { it.second.length in 1..maxLength && seen.add(it.second) }
}

private val MARKER = Regex("</?(caret|selection|block|warning|weak_warning|error|info|fold|ref|cursor|spot|lineMarker|inject)\\b[^>]*>")

/** The edits of a brute force at one offset: each insertion, and the deletions of each length in [deletions]. */
internal fun yamlBruteForceChanges(text: String, offset: Int, insertions: List<String>, deletions: List<Int>): List<YamlChange> =
  insertions.map { YamlChange(TextRange(offset, offset), it) } +
  deletions.filter { offset + it <= text.length }.map { YamlChange(TextRange(offset, offset + it), "") }

/** The insertions of a hunt brute force, in addition to the insertions of the test. */
internal val YAML_HUNT_INSERTIONS = listOf(
  "x", "ab", " ", "  ", "\n", "\n  ", "\n    ", "\n- ", "\n  - ", "- ", "-", ": ", ":", "a: ", "k: v", "#", "# c",
  "\"", "'", "|", ">", "|-", ">+", "?", "? ", "{", "}", "[", "]", ",", "&a ", "*a", "!t ", "!!str ", "\t", "---", "...",
  "\\", "%", "@", "`", "\n\n", "\n#", "\n---\n", "''", "\"\"", "x: ", "1", "\u00e9", "\uD83D\uDE00",
)

/**
 * Compares the incremental reparse of a change with a full parse of the new text.
 * The in-memory check needs only a read action, so many threads can run it at the same time.
 * The commit check, the baseline and the minimization need the EDT.
 */
internal class YamlReparseOracle(private val project: Project) {
  fun createFile(text: String): PsiFileImpl {
    val file = LightVirtualFile("test.yaml", YAMLLanguage.INSTANCE, text)
    return PsiManager.getInstance(project).findFile(file) as PsiFileImpl
  }

  /** Returns the kind of the reparse of [change] in [file], and the tree dump of the incremental reparse if it is not a full reparse. */
  fun reparse(file: PsiFileImpl, text: String, change: YamlChange): Pair<YamlReparseKind, String?> {
    val newText = change.apply(text)
    val changedRange = ChangedPsiRangeUtil.getChangedPsiRange(file, file.treeElement!!, newText) ?: return YamlReparseKind.NONE to null
    val couple = BlockSupportImpl.findReparseableNodeAndReparseIt(file, file.node, changedRange, newText) ?: return YamlReparseKind.FULL to null
    val oldNode = couple.first
    val newNode = couple.second
    val kind = when {
      oldNode is LeafElement -> YamlReparseKind.LEAF
      oldNode.elementType is YamlBlockCollectionType -> YamlReparseKind.BLOCK
      else -> YamlReparseKind.OTHER
    }
    return kind to dumpTree(file.node, oldNode, newNode)
  }

  /** The tree dump of a full parse of [text], in the form of [reparse]. */
  fun fullTree(text: String): String = dumpTree(createFile(text).node, null, null)

  /** Commits [change] in a new file of [text]. Returns true if the committed tree differs from a parse of the new text. */
  fun isWrongAfterCommit(text: String, change: YamlChange): Boolean = commitDifference(text, change) != null

  /** Commits [change] in a new file of [text]. Returns the first difference of the committed tree from a parse of the new text, or null. */
  fun commitDifference(text: String, change: YamlChange): String? {
    val file = createFile(text)
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(file)!!
    CommandProcessor.getInstance().executeCommand(null, {
      WriteAction.run<Throwable> { document.replaceString(change.range.startOffset, change.range.endOffset, change.replacement) }
    }, null, null)
    documentManager.commitDocument(document)
    val expected = DebugUtil.psiToString(createFile(document.text), true)
    val actual = DebugUtil.psiToString(file, true)
    return if (expected == actual) null else firstTreeDifference(expected, actual)
  }

  /** True if the commit of [change] gives a wrong tree with the leaf reparse and the block reparse on, but not with them off. */
  fun isIncrementalFailure(text: String, change: YamlChange): Boolean =
    isWrongAfterCommit(text, change) && !withReparseOff { isWrongAfterCommit(text, change) }

  fun <T> withReparseOff(action: () -> T): T {
    val disposable = Disposer.newDisposable()
    try {
      Registry.get("yaml.reparse.leaves").setValue(false, disposable)
      Registry.get("yaml.reparse.collections").setValue(false, disposable)
      return action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  /**
   * Makes each change of [changesAt] at each offset of each text, in parallel read actions.
   * Each change whose reparse kind is in [kinds] must give the tree of a full parse.
   * Then on the EDT, commits each wrong change, drops the ones that are also wrong with the reparse off, and minimizes the rest.
   */
  suspend fun bruteForce(
    name: String,
    texts: List<Pair<String, String>>,
    kinds: Set<YamlReparseKind>,
    changesAt: (text: String, offset: Int) -> List<YamlChange>,
  ): YamlBruteForceResult {
    val chunks = texts.flatMap { (fileName, text) -> (0..text.length step CHUNK).map { Triple(fileName, text, it until minOf(text.length + 1, it + CHUNK)) } }
    val partial = withContext(Dispatchers.Default) {
      coroutineScope {
        chunks.map { (fileName, text, offsets) ->
          async {
            readAction {
              val file = createFile(text)
              val result = YamlBruteForceResult()
              for (offset in offsets) {
                for (change in changesAt(text, offset)) {
                  result.total++
                  try {
                    val (kind, actual) = reparse(file, text, change)
                    result.kinds.merge(kind, 1, Int::plus)
                    if (kind !in kinds || actual == null) continue
                    result.accepted++
                    if (actual != fullTree(change.apply(text))) result.candidates.add(Triple(fileName, text, change) to kind)
                  }
                  catch (e: Throwable) {
                    if (e is ProcessCanceledException || e is CancellationException) throw e
                    result.exceptions.putIfAbsent("${e.javaClass.name}:${e.message}:${escapeYaml(change.replacement)}",
                                                  "$fileName, change $change of '${escapeYaml(text)}': ${e.stackTraceToString().lines().take(12).joinToString("\n")}")
                  }
                }
              }
              result
            }
          }
        }.awaitAll()
      }
    }
    val result = YamlBruteForceResult()
    for (part in partial) {
      result.total += part.total
      result.accepted += part.accepted
      part.kinds.forEach { result.kinds.merge(it.key, it.value, Int::plus) }
      result.candidates.addAll(part.candidates)
      part.exceptions.forEach { result.exceptions.putIfAbsent(it.key, it.value) }
    }
    println("$name: ${texts.size} texts, ${result.total} changes, kinds ${result.kinds}, ${result.candidates.size} wrong in memory")
    for ((candidate, kind) in result.candidates.take(PRINTED_CANDIDATES)) println("WRONG IN MEMORY: $kind, ${candidate.first}, change ${candidate.third}")
    confirm(result)
    result.exceptions.values.forEach { result.failures.putIfAbsent(it, "EXCEPTION: $it") }
    println("$name: ${result.failures.size} distinct failures, ${result.baseline} also wrong without the incremental reparse, " +
            "${result.inMemoryOnly} wrong only in memory, ${result.unchecked} not committed because of the limits")
    for (failure in result.failures.values) println("FAILURE: $failure\n")
    return result
  }

  /**
   * Runs on the EDT. Commits at most [COMMITTED_PER_KEY] wrong changes of each replacement,
   * and minimizes at most [MINIMIZED_PER_KEY] of them. All the minimizations together take at most [minimizeTime].
   */
  private fun confirm(result: YamlBruteForceResult) {
    val committedPerKey = HashMap<String, Int>()
    val minimizedPerKey = HashMap<String, Int>()
    val deadline = System.nanoTime() + minimizeTime.inWholeNanoseconds
    for ((candidate, kind) in result.candidates) {
      val (fileName, text, change) = candidate
      val key = "$kind:${escapeYaml(change.replacement)}:${change.range.length}"
      if ((committedPerKey[key] ?: 0) >= COMMITTED_PER_KEY || (minimizedPerKey[key] ?: 0) >= MINIMIZED_PER_KEY || result.failures.size >= MAX_FAILURES) {
        result.unchecked++
        continue
      }
      committedPerKey.merge(key, 1, Int::plus)
      val difference = commitDifference(text, change)
      if (difference == null) {
        result.inMemoryOnly++
        continue
      }
      if (withReparseOff { isWrongAfterCommit(text, change) }) {
        result.baseline++
        continue
      }
      minimizedPerKey.merge(key, 1, Int::plus)
      val (minText, minChange) = minimizeYamlCase(text, change, deadline, ::isIncrementalFailure)
      val stopped = if (System.nanoTime() > deadline) " (stopped by the time limit)" else ""
      val minKind = ReadAction.compute<YamlReparseKind, Throwable> { reparse(createFile(minText), minText, minChange).first }
      val label = if (minKind == kind) "$kind" else "$minKind (first seen as $kind)"
      result.failures.putIfAbsent("$minKind:${escapeYaml(minText)}:$minChange",
                                  "$label MINIMIZED$stopped: change $minChange of '${escapeYaml(minText.substring(minChange.range.startOffset, minChange.range.endOffset))}' in '${escapeYaml(minText)}'\n" +
                                  "first seen: $fileName, change $change\n${commitDifference(minText, minChange) ?: difference}")
    }
  }
}

private val minimizeTime: Duration = if (YamlReparseHunt.wide) 10.minutes else 1.minutes

/** The counts of a brute force, the wrong changes of the in-memory check, and the distinct failures after the commit check. */
internal class YamlBruteForceResult {
  var total = 0
  var accepted = 0
  val kinds = HashMap<YamlReparseKind, Int>()
  val candidates = ArrayList<Pair<Triple<String, String, YamlChange>, YamlReparseKind>>()
  val failures = LinkedHashMap<String, String>()
  val exceptions = LinkedHashMap<String, String>()
  var baseline = 0
  var inMemoryOnly = 0
  var unchecked = 0
}

/**
 * Dumps the tree of [root] with the type, the PSI class, the error description, the range and the leaf text of each node.
 * If [oldNode] is not null, dumps [newNode] in its place, so the result is the tree after a merge of the reparse.
 * The merge keeps [oldNode], so the dump takes the PSI class of [oldNode].
 */
private fun dumpTree(root: ASTNode, oldNode: ASTNode?, newNode: ASTNode?): String = buildString {
  fun visit(node: ASTNode, start: Int, depth: Int): Int {
    val current = if (node === oldNode) newNode!! else node
    append("  ".repeat(depth)).append(current.elementType).append(' ').append(node.psi?.javaClass?.simpleName)
    if (current is PsiErrorElement) append(" '").append(current.errorDescription).append('\'')
    val child = current.firstChildNode
    if (child == null) {
      val text = current.text
      append(' ').append(start).append('-').append(start + text.length).append(" '").append(escapeYaml(text)).append("'\n")
      return start + text.length
    }
    val headerEnd = length
    append('\n')
    var offset = start
    var c: ASTNode? = child
    while (c != null) {
      offset = visit(c, offset, depth + 1)
      c = c.treeNext
    }
    insert(headerEnd, " $start-$offset")
    return offset
  }
  visit(root, 0, 0)
}

/**
 * Removes whole lines, then chars, outside of the change while [fails] stays true.
 * Stops at [deadline], a value of [System.nanoTime], and returns the smallest case so far.
 */
internal fun minimizeYamlCase(
  text: String,
  change: YamlChange,
  deadline: Long = Long.MAX_VALUE,
  fails: (String, YamlChange) -> Boolean,
): Pair<String, YamlChange> {
  var currentText = text
  var currentRange = change.range

  fun tryRemove(start: Int, end: Int): Boolean {
    val overlaps = start < currentRange.endOffset && end > currentRange.startOffset ||
                   start == currentRange.startOffset && currentRange.isEmpty
    if (overlaps) return false
    val candidate = currentText.removeRange(start, end)
    val candidateRange = if (end <= currentRange.startOffset) currentRange.shiftLeft(end - start) else currentRange
    if (!fails(candidate, YamlChange(candidateRange, change.replacement))) return false
    currentText = candidate
    currentRange = candidateRange
    return true
  }

  var changed = true
  while (changed && System.nanoTime() < deadline) {
    changed = false
    var lineStart = 0
    while (lineStart < currentText.length && System.nanoTime() < deadline) {
      val lineEnd = currentText.indexOf('\n', lineStart).let { if (it < 0) currentText.length else it + 1 }
      if (tryRemove(lineStart, lineEnd)) changed = true else lineStart = lineEnd
    }
    for (length in listOf(8, 4, 2, 1)) {
      var start = 0
      while (start + length <= currentText.length && System.nanoTime() < deadline) {
        if (tryRemove(start, start + length)) changed = true else start++
      }
    }
  }
  return currentText to YamlChange(currentRange, change.replacement)
}

internal fun firstTreeDifference(expected: String, actual: String): String {
  val expectedLines = expected.lines()
  val actualLines = actual.lines()
  val index = expectedLines.indices.firstOrNull { expectedLines[it] != actualLines.getOrNull(it) } ?: expectedLines.size
  val from = maxOf(0, index - 3)
  return "expected:\n" + expectedLines.subList(from, minOf(expectedLines.size, index + 4)).joinToString("\n") +
         "\nactual:\n" + actualLines.subList(minOf(from, actualLines.size), minOf(actualLines.size, index + 4)).joinToString("\n")
}

internal fun escapeYaml(text: CharSequence): String = text.toString().replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r")

private const val CHUNK = 200
private const val MINIMIZED_PER_KEY = 3
private const val COMMITTED_PER_KEY = 20
private const val PRINTED_CANDIDATES = 20
private const val MAX_FAILURES = 100
