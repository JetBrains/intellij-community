// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.python.pyproject.model.internal.workspaceBridge.findInnermost
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.random.Random

/**
 * Pins [findInnermost] against the full scan that it replaced (PY-91841).
 *
 * The scan read `allContentRoots.filter { path.startsWith(it.url.toPath()) }.maxByOrNull { it.url.url.length }`.
 * Every url carries one scheme, so the longest url of a candidate set is the longest path of that set.
 * [reference] is that expression over a path.
 *
 * A case builds every path with the `/` operator and keeps it relative, so the separator of the running
 * system applies. One case needs a real filesystem root, and it asks the default filesystem for one.
 */
internal class FindInnermostTest {

  /** The behaviour of the full scan that [findInnermost] replaced. */
  private fun reference(path: Path, byPath: Map<Path, String>): String? =
    byPath.keys.filter { path.startsWith(it) }.maxByOrNull { it.toString().length }?.let { byPath[it] }

  private fun check(path: Path, byPath: Map<Path, String>, expected: String?) {
    assertThat(findInnermost(path, byPath)).describedAs("findInnermost of $path").isEqualTo(expected)
    assertThat(findInnermost(path, byPath)).describedAs("the full scan of $path disagrees").isEqualTo(reference(path, byPath))
  }

  @Test
  fun testTheDeepestAncestorWins() {
    val a = Path("a")
    val ab = a / "b"
    val abc = ab / "c"
    val byPath = mapOf(a to "a", ab to "ab", abc to "abc")
    check(abc / "d" / "e", byPath, "abc")
    check(ab / "x", byPath, "ab")
    check(a / "x", byPath, "a")
  }

  @Test
  fun testAnExactMatchWins() {
    val a = Path("a")
    val ab = a / "b"
    check(ab, mapOf(a to "a", ab to "ab"), "ab")
  }

  @Test
  fun testNoAncestorGivesNull() {
    check(Path("other") / "place", mapOf(Path("a") to "a"), null)
    check(Path("a"), emptyMap(), null)
  }

  /**
   * A sibling whose name is a string prefix is not an ancestor. A comparison of the raw strings would take
   * `root/proj` for an ancestor of `root/project/src`.
   */
  @Test
  fun testAPrefixNameIsNotAnAncestor() {
    val root = Path("root")
    val byPath = mapOf(root / "proj" to "proj", root / "project" to "project")
    check(root / "project" / "src", byPath, "project")
    check(root / "projX" / "src", byPath, null)
  }

  /**
   * A longer url does not always mean a deeper path, and the full scan took the longest url. The two agree
   * here, because two candidates of one path are always comparable and so form one chain.
   */
  @Test
  fun testALongNameOfASiblingDoesNotWin() {
    val a = Path("a")
    val longSibling = a / "bbbbbbbbbbbbbbbb"
    val byPath = mapOf(longSibling to "longSibling", a / "b" / "c" to "deep", a to "a")
    check(a / "b" / "c" / "d", byPath, "deep")
    check(longSibling / "z", byPath, "longSibling")
  }

  @Test
  fun testTheFilesystemRootIsAnAncestor() {
    val root = FileSystems.getDefault().rootDirectories.first()
    check(root / "a" / "b", mapOf(root to "root"), "root")
  }

  /** A random tree, to catch a case the cases above do not name. */
  @Test
  fun testAgreesWithTheFullScanOnARandomTree() {
    val random = Random(20260901)
    val names = listOf("a", "ab", "abc", "b", "proj", "project", "x")
    fun randomPath(depth: Int): Path =
      (2..depth).fold(Path(names.random(random))) { path, _ -> path / names.random(random) }
    repeat(300) {
      val byPath = (1..8).associate { index -> randomPath(random.nextInt(1, 4)) to "root$index" }
      val query = randomPath(random.nextInt(1, 6))
      assertThat(findInnermost(query, byPath))
        .describedAs("findInnermost disagrees with the full scan for $query over ${byPath.keys}")
        .isEqualTo(reference(query, byPath))
    }
  }
}
