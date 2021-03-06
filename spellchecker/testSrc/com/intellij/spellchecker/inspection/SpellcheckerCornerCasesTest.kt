// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspection

import com.intellij.spellchecker.SpellCheckerManager
import junit.framework.TestCase

class SpellcheckerCornerCasesTest : SpellcheckerInspectionTestCase() {
  fun `test a lot of mistakes in united word suggest`() {
    //should not end up with OOM
    val manager = SpellCheckerManager.getInstance(project)
    val suggestions = manager.getSuggestions("MYY_VERRY_LOOONG_WORDD_WOTH_A_LOTTT_OFFF_MISAKES")
    TestCase.assertTrue(suggestions.isNotEmpty())
  }
}