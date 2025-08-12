// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures.junit5

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getLookupFixtureManager
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.registerImplicitFixtures
import com.jetbrains.python.PyNames
import com.jetbrains.python.fixtures.junit5.metaInfo.TestMetaInfoExtension
import com.jetbrains.python.fixtures.junit5.metaInfo.TestMetaInfoExtension.Companion.getTestClassInfo
import com.jetbrains.python.fixtures.junit5.metaInfo.TestMetaInfoExtension.Companion.getTestMethodInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.extension.*
import java.nio.file.Path

/**
 * PyDefaultTestApplication is a test annotation used to initialize a shared application context
 * and enrich it with various predefined test extensions (project, module, source root, PsiFile, editor, etc).
 *
 * It initializes a shared [com.intellij.openapi.application.Application] instance before any tests are run
 * and disposes it after all tests finish, through the [TestApplication] annotation.
 */
@TestApplication
@ExtendWith(LookupFixtureExtension::class)
@ExtendWith(TestMetaInfoExtension::class)
@ExtendWith(PyWithDefaultFixturesExtension::class)
annotation class PyDefaultTestApplication

private const val DEFAULT_PROJECT: String = "DEFAULT_PROJECT"
private const val DEFAULT_PY_MODULE: String = "DEFAULT_PY_MODULE"
private const val DEFAULT_SOURCE_ROOT: String = "DEFAULT_SOURCE_ROOT"
private const val DEFAULT_EDITOR: String = "DEFAULT_EDITOR"

private class PyWithDefaultFixturesExtension : BeforeAllCallback, BeforeEachCallback, Extension {

  /**
   * Sets up the necessary fixtures and configurations before all tests in the given context. This involves:
   * - Initializing necessary test project and module fixtures (reuses explicit fixtures if present).
   * - Setting up project and source root fixtures (reuses explicit fixtures if present).
   * - Registering all implicitly created fixtures.
   * - Waiting until all indexes are fully ready for use.
   */
  override fun beforeAll(context: ExtensionContext) {
    val manager = context.getLookupFixtureManager()

    val implicitFixtures = mutableListOf<LookupFixture>()

    val project = manager.getOrDefault {
      projectFixture(openAfterCreation = true).also {
        implicitFixtures += LookupFixture(DEFAULT_PROJECT, it, true)
      }
    }

    val module = manager.getOrDefault {
      project.moduleFixture(name = context.uniqueId, moduleType = PyNames.PYTHON_MODULE_ID).also {
        implicitFixtures += LookupFixture(DEFAULT_PY_MODULE, it, true)
      }
    }

    manager.getOrDefault {
      module.sourceRootFixture(
        pathFixture = project.pathInProjectFixture(Path.of("")),
        blueprintResourcePath = context.getTestClassInfo().testDataPath
      ).also {
        implicitFixtures += LookupFixture(DEFAULT_SOURCE_ROOT, it, true)
      }
    }

    runBlocking {
      context.registerImplicitFixtures(implicitFixtures)
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project.get())
  }


  /**
   * Sets up the necessary fixtures and configurations for a test case before it is executed.
   * This method initializes and registers implicit test fixtures (e.g., PSI file fixture, editor fixture),
   * configures the editor caret and selection state, ensures documents are saved and committed,
   * and waits for indexes to be ready.
   */
  override fun beforeEach(context: ExtensionContext) {
    val testMethodInfo = context.getTestMethodInfo()
    val testCaseFilePath = testMethodInfo.testCaseFilePath ?: return

    val implicitFixtures = mutableListOf<LookupFixture>()

    val classLevelManager = context.parent.get().getLookupFixtureManager()
    val sourceRoot: TestFixture<PsiDirectory> = classLevelManager.getRequired()

    val psiFileFixture = sourceRoot.psiFileFixture(testCaseFilePath).also {
      implicitFixtures += LookupFixture(testCaseFilePath.fileName.toString(), it, true)
    }

    val editorFixture = psiFileFixture.editorFixture().also {
      implicitFixtures += LookupFixture(DEFAULT_EDITOR, it, true)
    }

    runBlocking {
      context.registerImplicitFixtures(implicitFixtures)
    }

    val project = classLevelManager.getRequired<Project>().get()
    runBlocking {
      withContext(Dispatchers.EDT) {
        val editor = editorFixture.get()
        val document = editor.document

        val caretState = EditorTestUtil.extractCaretAndSelectionMarkers(document)
        EditorTestUtil.setCaretsAndSelection(editor, caretState)

        FileDocumentManager.getInstance().saveDocument(document)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }
}

