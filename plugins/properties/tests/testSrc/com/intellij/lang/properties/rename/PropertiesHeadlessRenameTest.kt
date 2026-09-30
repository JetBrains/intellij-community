// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.rename

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.refactoring.rename.RenamePropertyProcessor
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.HeadlessRenameProcessor
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor
import com.intellij.refactoring.rename.HeadlessRenameResult
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertiesHeadlessRenameTest : BasePlatformTestCase() {
  fun `test key is renamed in every file of the bundle`() {
    val base = addPropertiesFile("messages.properties", "greeting=Hello\nfarewell=Bye\n")
    val german = addPropertiesFile("messages_de.properties", "greeting=Hallo\nfarewell=Tschuess\n")
    val greeting = findProperty(base, "greeting")
    assertInstanceOf(HeadlessRenamePsiElementProcessor.processorOf(greeting), RenamePropertyProcessor::class.java)

    val result = performRename(greeting, "welcome")

    assertInstanceOf(result, HeadlessRenameResult.Applied::class.java)
    assertEquals("welcome=Hello\nfarewell=Bye\n", base.containingFile.text)
    assertEquals("welcome=Hallo\nfarewell=Tschuess\n", german.containingFile.text)
  }

  fun `test key that another property of the bundle holds is refused`() {
    val base = addPropertiesFile("messages.properties", "greeting=Hello\n")
    val german = addPropertiesFile("messages_de.properties", "greeting=Hallo\nwelcome=Willkommen\n")

    val result = HeadlessRenameProcessor.analyze(project, findProperty(base, "greeting"), "welcome")

    val refused = assertInstanceOf(result, HeadlessRenameResult.Refused::class.java)
    assertFalse("a refusal must name its conflict", refused.conflicts.isEmpty())
    assertEquals("greeting=Hello\n", base.containingFile.text)
    assertEquals("greeting=Hallo\nwelcome=Willkommen\n", german.containingFile.text)
  }

  private fun addPropertiesFile(name: String, text: String): PropertiesFile =
    myFixture.addFileToProject(name, text) as PropertiesFile

  private fun findProperty(file: PropertiesFile, key: String): PsiElement =
    file.findPropertyByKey(key)?.psiElement ?: error("no property $key in ${file.name}")

  private fun performRename(element: PsiElement, newName: String): HeadlessRenameResult {
    val planned = HeadlessRenameProcessor.analyze(project, element, newName)
    val plan = assertInstanceOf(planned, HeadlessRenameResult.Planned::class.java)
    return plan.plan.apply()
  }
}
