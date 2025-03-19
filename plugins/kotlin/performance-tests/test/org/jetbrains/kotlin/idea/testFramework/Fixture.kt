// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testFramework

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.lang.ExternalAnnotatorsFilter
import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.xml.XmlFileNSInfoProvider
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.fixtures.EditorTestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.usages.Usage
import com.intellij.xml.XmlSchemaProvider
import junit.framework.TestCase.*
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.perf.suite.CursorConfig
import org.jetbrains.kotlin.idea.perf.suite.TypingConfig
import org.jetbrains.kotlin.idea.performance.tests.utils.*
import org.jetbrains.kotlin.idea.performance.tests.utils.project.openInEditor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

class Fixture(
    val fileName: String,
    val project: Project,
    val editor: Editor,
    val psiFile: PsiFile,
    val vFile: VirtualFile = psiFile.virtualFile
) : AutoCloseable {
    private var delegate = EditorTestFixture(project, editor, vFile)

    var savedText: String? = null
        get() = field
        private set(value) {
            field = value
        }

    val document: Document
        get() = editor.document

    val text: String
        get() = document.text

    init {
        storeText()
    }

    fun simpleFilename(): String {
        val lastIndexOf = fileName.lastIndexOf('/')
        return if (lastIndexOf >= 0) fileName.substring(lastIndexOf + 1) else fileName
    }

    fun doHighlighting(): List<HighlightInfo> = delegate.doHighlighting()

    fun findUsages(psiElement: PsiElement): Set<Usage> {
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
        val handler = findUsagesManager.getNewFindUsagesHandler(psiElement, false) ?: error("unable to find FindUsagesHandler")
        return findUsagesManager.doFindUsages(arrayOf(psiElement), emptyArray(), handler, handler.findUsagesOptions, false).usages
    }

    fun type() {
        val string = typingConfig.insertString ?: error("insertString has to be specified")
        for (i in string.indices) {
            type(string[i])
            typingConfig.delayMs?.let(Thread::sleep)
        }
    }

    fun type(s: String) {
        delegate.type(s)
    }

    fun type(c: Char) {
        delegate.type(c)
    }

    fun typeAndHighlight(): List<HighlightInfo> {
        type()
        return doHighlighting()
    }

    fun moveCursor() {
        val tasksIdx = cursorConfig.marker?.let { marker ->
            text.indexOf(marker).also {
                check(it > 0) { "marker '$marker' not found in ${fileName}" }
            }
        } ?: 0

        editor.caretModel.moveToOffset(tasksIdx)
    }

    fun performEditorAction(actionId: String): Boolean {
        selectEditor()
        return delegate.performEditorAction(actionId)
    }

    fun complete(type: CompletionType = CompletionType.BASIC, invocationCount: Int = 1): Array<LookupElement> =
        delegate.complete(type, invocationCount) ?: emptyArray()

    fun storeText() {
        savedText = text
    }

    fun restoreText() {
        savedText?.let {
            try {
                applyText(it)
            } finally {
                project.cleanupCaches()
            }
        }
    }

    fun revertChanges(revertChangesAtTheEnd: Boolean = true, text: String) {
        try {
            if (revertChangesAtTheEnd) {
                // TODO: [VD] revert ?
                //editorFixture.performEditorAction(IdeActions.SELECTED_CHANGES_ROLLBACK)
                applyText(text)
            }
        } finally {
            project.cleanupCaches()
        }
    }

    fun selectMarkers(initialMarker: String?, finalMarker: String?) {
        selectEditor()
        val text = editor.document.text
        editor.selectionModel.setSelection(
            initialMarker?.let { marker -> text.indexOf(marker) } ?: 0,
            finalMarker?.let { marker -> text.indexOf(marker) } ?: text.length)
    }

    private fun selectEditor() {
        val fileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
        fileEditorManagerEx.openFile(vFile, true)
        check(fileEditorManagerEx.selectedEditor?.file == vFile) { "unable to open $vFile" }
    }

    fun applyText(text: String) {
        runWriteAction {
            document.setText(text)
            saveDocument(document)
            commitDocument(project, document)
        }
        dispatchAllInvocationEvents()
    }

    fun openInEditor() {
        openInEditor(project, vFile)
    }

    fun updateScriptDependenciesIfNeeded() {
        if (isAKotlinScriptFile(fileName)) {
            runAndMeasure("update script dependencies for $fileName") {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(psiFile)
            }
        }
    }

    override fun close() {
        savedText = null
        project.close(vFile)
    }

    val cursorConfig = CursorConfig(this)

    val typingConfig = TypingConfig(this)

    companion object {
        // quite simple impl - good so far
        fun isAKotlinScriptFile(fileName: String) = fileName.endsWith(KotlinParserDefinition.STD_SCRIPT_EXT)

        fun Project.cleanupCaches() {
            commitAllDocuments()
            PsiManager.getInstance(this).dropPsiCaches()
        }

        fun Project.close(file: PsiFile) {
            close(file.virtualFile)
        }

        fun Project.close(file: VirtualFile) {
            FileEditorManager.getInstance(this).closeFile(file)
        }

        fun enableAnnotatorsAndLoadDefinitions(project: Project) {
            ReferenceProvidersRegistry.getInstance() // pre-load tons of classes
            InjectedLanguageManager.getInstance(project) // zillion of Dom Sem classes
            with(LanguageAnnotators.INSTANCE) {
                allForLanguage(JavaLanguage.INSTANCE) // pile of annotator classes loads
                allForLanguage(XMLLanguage.INSTANCE)
                allForLanguage(KotlinLanguage.INSTANCE)
            }
            DaemonAnalyzerTestCase.assertTrue(
                "side effect: to load extensions",
                ProblemHighlightFilter.EP_NAME.extensions.toMutableList()
                    .plus(ImplicitUsageProvider.EP_NAME.extensions)
                    .plus(XmlSchemaProvider.EP_NAME.extensions)
                    .plus(XmlFileNSInfoProvider.EP_NAME.extensions)
                    .plus(ExternalAnnotatorsFilter.EXTENSION_POINT_NAME.extensions)
                    .plus(IndexPatternBuilder.EP_NAME.extensions).isNotEmpty()
            )

            // side effect: to load script definitions"
            val scriptDefinitionsManager = ScriptDefinitionsManager.getInstance(project)
            scriptDefinitionsManager.allDefinitions
            dispatchAllInvocationEvents()

            //assertFalse(KotlinScriptingSettings.getInstance(project).isAutoReloadEnabled)
        }


        fun openFixture(project: Project, fileName: String): Fixture {
            val fileInEditor = openInEditor(project, fileName)
            val file = fileInEditor.psiFile
            val editorFactory = EditorFactory.getInstance()
            val editor = editorFactory.getEditors(fileInEditor.document, project)[0]

            return Fixture(fileName, project, editor, file)
        }

        fun openFixture(project: Project, file: VirtualFile, fileName: String? = null): Fixture {
            val fileInEditor = openInEditor(project, file)
            val psiFile = fileInEditor.psiFile
            val editorFactory = EditorFactory.getInstance()
            val editor = editorFactory.getEditors(fileInEditor.document, project)[0]
            return Fixture(fileName ?: project.relativePath(file), project, editor, psiFile)
        }

        /**
         * @param lookupElements perform basic autocompletion and check presence of suggestion if elements are not empty
         */
        fun typeAndCheckLookup(
            project: Project,
            fileName: String,
            marker: String,
            insertString: String,
            surroundItems: String = "\n",
            lookupElements: List<String>,
            revertChangesAtTheEnd: Boolean = true
        ) {
            val fileInEditor = openInEditor(project, fileName)
            val editor = EditorFactory.getInstance().getEditors(fileInEditor.document, project)[0]
            val fixture = Fixture(fileName, project, editor, fileInEditor.psiFile)

            val initialText = editor.document.text
            try {
                if (isAKotlinScriptFile(fileName)) {
                    ScriptConfigurationManager.updateScriptDependenciesSynchronously(fileInEditor.psiFile)
                }

                val tasksIdx = fileInEditor.document.text.indexOf(marker)
                assertTrue(tasksIdx > 0)
                editor.caretModel.moveToOffset(tasksIdx + marker.length + 1)

                for (surroundItem in surroundItems) {
                    EditorTestUtil.performTypingAction(editor, surroundItem)
                }

                editor.caretModel.moveToOffset(editor.caretModel.offset - 1)
                fixture.type(insertString)

                if (lookupElements.isNotEmpty()) {
                    val elements = fixture.complete()
                    val items = elements.map { it.lookupString }.toList()
                    for (lookupElement in lookupElements) {
                        assertTrue("'$lookupElement' has to be present in items", items.contains(lookupElement))
                    }
                }
            } finally {
                // TODO: [VD] revert ?
                //fixture.performEditorAction(IdeActions.SELECTED_CHANGES_ROLLBACK)
                if (revertChangesAtTheEnd) {
                    runWriteAction {
                        editor.document.setText(initialText)
                        commitDocument(project, editor.document)
                    }
                }
            }
        }

    }
}


fun KotlinLightCodeInsightFixtureTestCase.removeInfoMarkers() {
    ExpectedHighlightingData(editor.document, true, true).init()

    runInEdtAndWait {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}
