package com.intellij.mermaid.fus

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.mermaid.MermaidPlugin
import com.intellij.mermaid.lang.MermaidFileType
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.psi.traverse
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.concurrency.NonUrgentExecutor
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

private val reportingTimeout = 1.seconds
private val DocumentReported = Key.create<Long>("Mermaid.DocumentReported")

internal class DiagramReportingFactoryListener: EditorFactoryListener {
  override fun editorReleased(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project ?: return
    val document = editor.document
    val timestamp = document.getUserData(DocumentReported) ?: 0
    val time = System.currentTimeMillis()
    if (time - timestamp < reportingTimeout.inWholeMilliseconds.also { println(it) }) {
      return
    }
    document.putUserData(DocumentReported, time)
    report(project, document)
  }

  private fun report(project: Project, document: Document) {
    MermaidPlugin.coroutineScope(project).launch {
      withContext(NonUrgentExecutor.getInstance().asCoroutineDispatcher()) {
        readAction {
          val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@readAction
          when (file) {
            is MermaidFile -> reportFromMermaidFile(file)
            else -> reportFromRegularFile(file)
          }
        }
      }
    }
  }

  private fun reportFromMermaidFile(file: MermaidFile) {
    val type = file.obtainDiagramType() ?: return
    MermaidCollector.reportDiagramUsed(type, MermaidFileType)
  }

  private fun reportFromRegularFile(file: PsiFile) {
    val files = collectInjectedMermaidFiles(file)
    val types = files.mapNotNull { it.obtainDiagramType() }
    for (type in types) {
      MermaidCollector.reportDiagramUsed(type, file.fileType)
    }
    MermaidCollector.reportInjectedDiagrams(
      types = types.distinct().toList(),
      file = file.fileType,
      count = types.count()
    )
  }

  private fun collectInjectedMermaidFiles(file: PsiFile): Sequence<MermaidFile> {
    val hosts = file.traverse().filterIsInstance<PsiLanguageInjectionHost>()
    val manager = InjectedLanguageManager.getInstance(file.project)
    val entries = hosts.mapNotNull { manager.getInjectedPsiFiles(it) }.flatten()
    return entries.map{ it.first }.filterIsInstance<MermaidFile>()
  }
}
