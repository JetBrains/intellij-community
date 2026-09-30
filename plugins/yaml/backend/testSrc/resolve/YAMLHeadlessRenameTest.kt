// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.HeadlessRenameProcessor
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor
import com.intellij.refactoring.rename.HeadlessRenameResult
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLAnchor

class YAMLHeadlessRenameTest : BasePlatformTestCase() {
  fun `test anchor is renamed with its aliases`() {
    myFixture.configureByText("test.yml", "def: &cur val\nuse1: *cur\nuse2: *cur\n")
    val anchor = findAnchor("cur")
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(anchor), YAMLRenamePsiElementProcessor::class.java)

    val result = performRename(anchor, "zzz")

    assertInstanceOf(result, HeadlessRenameResult.Applied::class.java)
    myFixture.checkResult("def: &zzz val\nuse1: *zzz\nuse2: *zzz\n")
  }

  fun `test anchor that hides an alias of a previous anchor is refused`() {
    val text = "def1: &prev val1\ndef2: &cur val2\nuse1: *prev\nuse2: *cur\n"
    myFixture.configureByText("test.yml", text)

    val result = HeadlessRenameProcessor.analyze(project, findAnchor("cur"), "prev")

    val refused = assertInstanceOf(result, HeadlessRenameResult.Refused::class.java)
    assertFalse("a refusal must name its conflict", refused.conflicts.isEmpty())
    myFixture.checkResult(text)
  }

  fun `test anchor whose alias a next anchor takes over is refused`() {
    val text = "def1: &cur val1\nuse1: *cur\ndef2: &post val2\nuse2: *cur\n"
    myFixture.configureByText("test.yml", text)

    val result = HeadlessRenameProcessor.analyze(project, findAnchor("cur"), "post")

    val refused = assertInstanceOf(result, HeadlessRenameResult.Refused::class.java)
    assertFalse("a refusal must name its conflict", refused.conflicts.isEmpty())
    myFixture.checkResult(text)
  }

  private fun findAnchor(name: String): YAMLAnchor =
    PsiTreeUtil.collectElementsOfType(myFixture.file, YAMLAnchor::class.java).single { it.name == name }

  private fun performRename(element: PsiElement, newName: String): HeadlessRenameResult {
    val planned = HeadlessRenameProcessor.analyze(project, element, newName)
    val plan = assertInstanceOf(planned, HeadlessRenameResult.Planned::class.java)
    return plan.plan.apply()
  }
}
