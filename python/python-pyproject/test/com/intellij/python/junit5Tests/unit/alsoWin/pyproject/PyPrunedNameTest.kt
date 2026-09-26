// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.python.pyproject.model.internal.pyProjectToml.isPrunedName
import com.jetbrains.python.venvReader.PRUNED_SCAN_DIRS
import com.jetbrains.python.venvReader.PRUNED_SCAN_DIRS_NO_DOT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the one name rule that the search and the subtree load of PY-91841 both read.
 *
 * The two used to hold a copy of the rule each, and a copy that prunes more hides a `pyproject.toml` from
 * the search. The model then loses that module without an error.
 */
internal class PyPrunedNameTest {

  /**
   * The rule tests `PRUNED_SCAN_DIRS_NO_DOT` and the dot, not `PRUNED_SCAN_DIRS`. That is the same answer
   * only while every other name of the full list starts with a dot. A name such as `build` added to the
   * full list alone would pass the rule, so this test fails instead.
   */
  @Test
  fun testTheDotTestCoversTheRestOfTheFullList() {
    val uncovered = PRUNED_SCAN_DIRS.filterNot { it.startsWith(".") || it in PRUNED_SCAN_DIRS_NO_DOT }
    assertThat(uncovered)
      .describedAs("add these names to PRUNED_SCAN_DIRS_NO_DOT, or isPrunedName stops pruning them")
      .isEmpty()
  }

  @Test
  fun testEveryNameOfTheFullListIsPruned() {
    for (name in PRUNED_SCAN_DIRS) {
      assertThat(name.isPrunedName()).describedAs(name).isTrue()
    }
  }

  @Test
  fun testADotNameIsPruned() {
    assertThat(".venv".isPrunedName()).isTrue()
    assertThat(".anything".isPrunedName()).isTrue()
  }

  @Test
  fun testAnOrdinaryNameIsKept() {
    assertThat("src".isPrunedName()).describedAs("src").isFalse()
    assertThat("my-project".isPrunedName()).describedAs("my-project").isFalse()
    assertThat("node_modules2".isPrunedName()).describedAs("a name that only starts like a pruned one").isFalse()
  }
}
