package org.intellij.plugins.markdown.inspections

import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.markdown.backend.inspections.MarkdownSkillFrontMatterInspection
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test

class MarkdownSkillFrontMatterInspectionTest: LightPlatformCodeInsightFixture4TestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(MarkdownSkillFrontMatterInspection())
  }

  @Test
  fun `test valid front matter`() = checkHighlighting("""
    ---
    name: valid-skill
    description: Does useful work.
    ---
  """.trimIndent())

  @Test
  fun `test quoted values`() = checkHighlighting("""
    ---
    name: "valid-skill"
    description: 'Does useful work.'
    ---
  """.trimIndent())

  @Test
  fun `test inspection only applies to SKILL md`() {
    myFixture.configureByText("README.md", "text")
    myFixture.checkHighlighting()
  }

  @Test
  fun `test add missing front matter`() {
    applyFix(
      "My Skill/SKILL.md",
      "<warning descr=\"Skill must start with YAML front matter\"># Skill</warning>",
      "Insert skill front matter"
    )
    myFixture.checkResult("""
      ---
      name: my-skill
      description: <caret>
      ---
      # Skill
    """.trimIndent())
  }

  @Test
  fun `test empty delimiters are not front matter`() {
    applyFix("valid-skill/SKILL.md", """
      <warning descr="Skill must start with YAML front matter">---
      ---

      Some text</warning>
    """.trimIndent(), "Insert skill front matter")
    myFixture.checkResult("""
      ---
      name: valid-skill
      description: <caret>
      ---

      Some text
    """.trimIndent())
  }

  @Test
  fun `test replace invalid name`() {
    applyFix("Valid Skill/SKILL.md", """
      ---
      <warning descr="`name` must be 1–64 lowercase letters, numbers, or hyphens and cannot start or end with a hyphen">name: Invalid Name!</warning>
      description: Useful.
      ---
    """.trimIndent(), "Update skill `name` to `valid-skill`")
    myFixture.checkResult("""
      ---
      name: valid-skill
      description: Useful.
      ---
    """.trimIndent())
  }

  @Test
  fun `test replace invalid double quoted name preserves quotes`() {
    applyFix("abc/SKILL.md", """
      ---
      <warning descr="`name` must be 1–64 lowercase letters, numbers, or hyphens and cannot start or end with a hyphen">name: "ab c"</warning>
      description: Useful.
      ---
    """.trimIndent(), "Update skill `name` to `abc`")
    myFixture.checkResult("""
      ---
      name: "abc"
      description: Useful.
      ---
    """.trimIndent())
  }

  @Test
  fun `test replace invalid single quoted name preserves quotes`() {
    applyFix("abc/SKILL.md", """
      ---
      <warning descr="`name` must be 1–64 lowercase letters, numbers, or hyphens and cannot start or end with a hyphen">name: 'ab c'</warning>
      description: Useful.
      ---
    """.trimIndent(), "Update skill `name` to `abc`")
    myFixture.checkResult("""
      ---
      name: 'abc'
      description: Useful.
      ---
    """.trimIndent())
  }

  @Test
  fun `test add missing name`() {
    applyFix("valid-skill/SKILL.md", """
      ---
      <warning descr="Skill front matter must contain a `name` field">description: Useful.</warning>
      ---
    """.trimIndent(), "Add mandatory `name` field")
    myFixture.checkResult("""
      ---
      name: valid-skill
      description: Useful.
      ---
    """.trimIndent())
  }

  @Test
  fun `test add missing description`() {
    applyFix("valid-skill/SKILL.md", """
      ---
      <warning descr="Skill front matter must contain a `description` field">name: valid-skill</warning>
      ---
    """.trimIndent(), "Add mandatory `description` field")
    myFixture.checkResult("""
      ---
      name: valid-skill
      description: <caret>
      ---
    """.trimIndent())
  }

  @Test
  fun `test blank description has no quick fix`() {
    checkEmptyDescription("")
  }

  @Test
  fun `test double quoted empty description has no quick fix`() {
    checkEmptyDescription("\"\"")
  }

  @Test
  fun `test single quoted empty description has no quick fix`() {
    checkEmptyDescription("''")
  }

  private fun checkEmptyDescription(value: String) {
    val file = myFixture.addFileToProject("valid-skill/SKILL.md", """
      ---
      name: valid-skill
      <warning descr="`description` must not be blank">description: $value</warning>
      ---
    """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.checkHighlighting()
    assertEmpty(myFixture.getAllQuickFixes().filterNot { it is EmptyIntentionAction })
  }

  private fun checkHighlighting(text: String) {
    myFixture.configureByText("SKILL.md", text)
    myFixture.checkHighlighting()
  }

  private fun applyFix(path: String, before: String, fixText: String) {
    val file = myFixture.addFileToProject(path, before)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.checkHighlighting()
    myFixture.launchAction(myFixture.getAllQuickFixes().first { it.text == fixText })
  }
}
