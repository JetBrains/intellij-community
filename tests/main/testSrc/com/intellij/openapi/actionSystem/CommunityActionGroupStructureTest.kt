// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.testFramework.actionSystem.ActionGroupStructureTestCase
import java.nio.file.Path
import kotlin.io.path.div

/**
 * [ActionGroupStructureTestCase] pinned to the community composition.
 */
class CommunityActionGroupStructureTest : ActionGroupStructureTestCase() {
  override val goldenFile: Path
    get() = communityRoot / "tests" / "main" / "testData" /
            "actionSystem" / "groupStructure" / "actionGroupStructure.txt"

  override val regenerateCommand: String
    get() = "(cd community && ./tests.cmd --module intellij.idea.community.main.tests" +
            " --test com.intellij.openapi.actionSystem.CommunityActionGroupStructureTest" +
            " -Dpass.actions.golden.regenerate=true)"
}
