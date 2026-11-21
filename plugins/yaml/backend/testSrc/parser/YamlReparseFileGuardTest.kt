// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.parser

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.syntax.psi.SyntaxFileElementType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.LanguageFileViewProviders
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YAMLParserDefinition
import org.jetbrains.yaml.psi.impl.YAMLFileImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Checks that the leaf reparse and the block reparse refuse a YAML tree that is not the only tree of a plain YAML file.
 * A template file has more than one language, and its YAML tree text is not the lexer text.
 * A file of another language can reuse the YAML tokens with its own file element type.
 * Each control case makes the same edit in a plain YAML file and gets the incremental reparse.
 */
@TestApplication
class YamlReparseFileGuardTest {
  private val project = projectFixture(openAfterCreation = true)

  @Test
  fun `a leaf edit in a plain YAML file reparses the leaf`(@TestDisposable disposable: Disposable) =
    check(Fixture.PLAIN, LEAF_EDIT, YamlReparseKind.LEAF, disposable)

  @Test
  fun `a block edit in a plain YAML file reparses the block`(@TestDisposable disposable: Disposable) =
    check(Fixture.PLAIN, BLOCK_EDIT, YamlReparseKind.BLOCK, disposable)

  @Test
  fun `a leaf edit in a file with two languages makes no leaf reparse`(@TestDisposable disposable: Disposable) =
    check(Fixture.TWO_LANGUAGES, LEAF_EDIT, null, disposable)

  @Test
  fun `a block edit in a file with two languages makes no block reparse`(@TestDisposable disposable: Disposable) =
    check(Fixture.TWO_LANGUAGES, BLOCK_EDIT, null, disposable)

  @Test
  fun `a leaf edit in a file of a YAML dialect makes no leaf reparse`(@TestDisposable disposable: Disposable) =
    check(Fixture.DIALECT, LEAF_EDIT, null, disposable)

  @Test
  fun `a block edit in a file of a YAML dialect makes no block reparse`(@TestDisposable disposable: Disposable) =
    check(Fixture.DIALECT, BLOCK_EDIT, null, disposable)

  /**
   * @param expected the expected kind of the reparse, or null for a reparse that is neither a leaf reparse nor a block reparse
   */
  private fun check(fixture: Fixture, edit: Pair<String, String>, expected: YamlReparseKind?, disposable: Disposable) = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      fixture.register(disposable)
      val caret = edit.first.indexOf("<caret>")
      val text = edit.first.replace("<caret>", "")
      val change = YamlChange(TextRange(caret, caret), edit.second)

      val kind = YamlReparseOracle(project.get()).reparse(yamlRoot(fixture, text), text, change).first
      if (expected != null) assertEquals(expected, kind, "the reparse kind")
      else assertFalse(kind == YamlReparseKind.LEAF || kind == YamlReparseKind.BLOCK, "the reparse kind $kind")

      val root = yamlRoot(fixture, text)
      val documentManager = PsiDocumentManager.getInstance(project.get())
      val document = documentManager.getDocument(root)!!
      CommandProcessor.getInstance().executeCommand(null, {
        WriteAction.run<Throwable> { document.replaceString(caret, caret, change.replacement) }
      }, null, null)
      documentManager.commitDocument(document)
      val expectedRoots = createFile(fixture, document.text).viewProvider.allFiles
      val actualRoots = root.viewProvider.allFiles
      assertEquals(expectedRoots.map { it.language }, actualRoots.map { it.language })
      for ((expectedRoot, actualRoot) in expectedRoots.zip(actualRoots)) {
        assertEquals(DebugUtil.psiToString(expectedRoot, true), DebugUtil.psiToString(actualRoot, true), "the tree of ${actualRoot.language}")
      }
    }
  }

  private fun yamlRoot(fixture: Fixture, text: String): PsiFileImpl {
    val file = createFile(fixture, text)
    return (file.viewProvider.getPsi(YAMLLanguage.INSTANCE) ?: file) as PsiFileImpl
  }

  private fun createFile(fixture: Fixture, text: String): PsiFile =
    PsiManager.getInstance(project.get()).findFile(LightVirtualFile(fixture.fileName, fixture.language, text))!!

  private enum class Fixture(val fileName: String, val language: Language) {
    PLAIN("test.yaml", YAMLLanguage.INSTANCE),
    TWO_LANGUAGES("test.txt", PlainTextLanguage.INSTANCE) {
      override fun register(disposable: Disposable) {
        LanguageFileViewProviders.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, FileViewProviderFactory { file, _, manager, eventSystemEnabled ->
          PlainTextAndYamlViewProvider(manager, file, eventSystemEnabled)
        }, disposable)
      }
    },
    DIALECT("test.yaml", TestYamlDialect) {
      override fun register(disposable: Disposable) {
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(TestYamlDialect, DialectParserDefinition(), disposable)
      }
    };

    open fun register(disposable: Disposable) = Unit
  }
}

private val LEAF_EDIT = "a: some<caret> text\nb: c\n" to "x"
private val BLOCK_EDIT = "a:\n  b: 1<caret>\n  c: 2\nd: 3\n" to "\n  e: 5"

/** A plain text file with a second root in YAML, as a template language file has. */
private class PlainTextAndYamlViewProvider(manager: PsiManager, file: VirtualFile, eventSystemEnabled: Boolean) :
  MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled) {
  override fun getBaseLanguage(): Language = PlainTextLanguage.INSTANCE

  override fun getLanguages(): Set<Language> = setOf(PlainTextLanguage.INSTANCE, YAMLLanguage.INSTANCE)

  override fun createFile(lang: Language): PsiFile? = LanguageParserDefinitions.INSTANCE.forLanguage(lang)?.createFile(this)

  override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
    PlainTextAndYamlViewProvider(manager, fileCopy, false)
}

/** A language that reuses the YAML tokens and the YAML parser with its own file element type. */
private object TestYamlDialect : Language(YAMLLanguage.INSTANCE, "TestYamlDialect")

private val DIALECT_FILE = SyntaxFileElementType(TestYamlDialect)

@Suppress("DEPRECATION")
private class DialectParserDefinition : YAMLParserDefinition() {
  override fun getFileNodeType() = DIALECT_FILE

  override fun createFile(viewProvider: FileViewProvider): PsiFile = DialectFile(viewProvider)
}

private class DialectFile(viewProvider: FileViewProvider) : YAMLFileImpl(viewProvider) {
  init {
    init(DIALECT_FILE, DIALECT_FILE)
  }
}
