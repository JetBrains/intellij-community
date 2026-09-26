package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.python.pyproject.model.internal.platformBridge.computeMinimalRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.Path

// Tests use relative paths so they behave identically on Windows and Unix
// while still exercising the same parent-chain / nameCount semantics.
class ComputeMinimalRootsTest {
  @Test
  fun emptyInputProducesEmptyResult() {
    assertEquals(emptySet<Path>(), computeMinimalRoots(emptySequence()))
  }

  @Test
  fun singlePathIsKept() {
    val only = Path("root", "project")
    assertEquals(setOf(only), computeMinimalRoots(sequenceOf(only)))
  }

  @Test
  fun duplicatePathsAreDeduplicated() {
    val a = Path("root", "project")
    assertEquals(setOf(a), computeMinimalRoots(sequenceOf(a, a, a)))
  }

  @Test
  fun descendantIsDroppedWhenAncestorComesFirst() {
    val ancestor = Path("root", "project")
    val descendant = Path("root", "project", "module")
    assertEquals(setOf(ancestor), computeMinimalRoots(sequenceOf(ancestor, descendant)))
  }

  @Test
  fun descendantIsDroppedWhenAncestorComesLast() {
    val ancestor = Path("root", "project")
    val descendant = Path("root", "project", "module")
    // Regression: the previous implementation kept both because it only
    // checked whether the new path was under an already-kept root, never
    // the reverse.
    assertEquals(setOf(ancestor), computeMinimalRoots(sequenceOf(descendant, ancestor)))
  }

  @Test
  fun unrelatedSiblingsAreAllKept() {
    val a = Path("root", "sub", "a")
    val b = Path("root", "sub", "b")
    val c = Path("other", "c")
    assertEquals(setOf(a, b, c), computeMinimalRoots(sequenceOf(a, b, c)))
  }

  @Test
  fun transitiveCoveringWorks() {
    val root = Path("root", "r")
    val mid = Path("root", "r", "m")
    val leaf = Path("root", "r", "m", "l")
    assertEquals(setOf(root), computeMinimalRoots(sequenceOf(leaf, mid, root)))
  }

  @Test
  fun siblingWithPrefixNameIsNotTreatedAsDescendant() {
    // "proj" is a string prefix of "project", but not a path ancestor.
    val proj = Path("root", "proj")
    val project = Path("root", "project")
    val projectSub = Path("root", "project", "sub")
    assertEquals(setOf(proj, project), computeMinimalRoots(sequenceOf(proj, project, projectSub)))
  }

  @Test
  fun testUnsorted() {
    val root = FileSystems.getDefault().rootDirectories.first()
    val paths = sequenceOf(
      root.resolve("windows"),
      root.resolve("windows", "foo"),
      root.resolve("Users"),
      root.resolve("windows", "bar"),
      root.resolve("windows2", "bar")
    )
    assertEquals(setOf(root.resolve("windows"), root.resolve("Users"), root.resolve("windows2", "bar")), computeMinimalRoots(paths))
  }

  @Test
  @EnabledOnOs(OS.WINDOWS, OS.MAC)
  fun testCaseWinMac() {
    testCaseSensitive(caseSensitive = false)
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  fun testCaseLinux() {
    testCaseSensitive(caseSensitive = true)
  }
}

private fun testCaseSensitive(caseSensitive: Boolean) {
  val root = FileSystems.getDefault().rootDirectories.first()
  val paths = sequenceOf(
    root.resolve("A"),
    root.resolve("a")
  )
  val actual = computeMinimalRoots(paths)
  val expected = buildSet {
    add(root.resolve("a"))
    if (caseSensitive) {
      add(root.resolve("A"))
    }
  }
  assertEquals(expected, actual)
}
