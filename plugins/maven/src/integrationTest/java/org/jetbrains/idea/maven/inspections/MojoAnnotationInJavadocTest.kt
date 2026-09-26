// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections

import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.javaCodeInsightFixture
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.setUpJdk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/maven/src/test/data/inspections/javadocMojoValidTags")
class MojoAnnotationInJavadocTest {
  companion object {
    @BeforeAll
    @JvmStatic
    @RunMethodInEdt
    fun beforeAll() {
      setUpJdk(LanguageLevel.JDK_1_8, project.get(), module, disposable)
    }

    private val disposable by disposableFixture()
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = true)
    private val module by project.moduleFixture(tempDir, addPathToSourceRoot = true)
  }

  private val fixture by javaCodeInsightFixture(project, tempDir)

  @BeforeEach
  fun setUp() {
    fixture.enableInspections(JavadocDeclarationInspection())
  }

  @Test
  fun testTestMojo() {
    fixture.configureByFile("TestMojo.java")
    fixture.checkHighlighting()
  }
}
