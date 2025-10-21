package org.jetbrains.idea.maven.editor

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlText
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.containers.nullize
import com.intellij.util.containers.tail
import org.jetbrains.idea.maven.editor.MavenModelVersionSynchronizerService.Companion.SKIP_COMMAND_KEY
import org.jetbrains.idea.maven.editor.MavenModelVersionSynchronizerService.Companion.SYNCHRONIZER_KEY

class MavenModelVersionSyncronizerImpl(
  private val editor: EditorImpl,
  private val project: Project,
) : DocumentListener, Disposable, CaretListener {
  companion object {
    private val MARKERS_KEY: Key<List<SynchronizationData>> = Key("maven.version.synchronizer.markers")
  }

  private var applying = false
  private val pdm = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase

  fun listenForDocumentChanges() {
    Disposer.register(editor.disposable, this)
    editor.document.addDocumentListener(this, this)
    val prev = editor.getUserData(SYNCHRONIZER_KEY)
    if (prev != null && prev !== this) {
      Disposer.dispose(prev)
    }
    editor.putUserData(SYNCHRONIZER_KEY, this)
  }

  override fun dispose() {
    editor.putUserData(SYNCHRONIZER_KEY, null)
  }

  override fun caretRemoved(event: CaretEvent) {
    val caret = event.getCaret()
    clearMarkers(caret)
  }

  override fun beforeDocumentChange(event: DocumentEvent) {
    val document = event.document
    val ideProject = project
    if (applying
        || ideProject.isDefault
        || UndoManager.getInstance(ideProject).isUndoInProgress
        || document.isInBulkUpdate) {
      return
    }

    if (document.getUserData(SKIP_COMMAND_KEY) == true) {
      return
    }

    val offset = event.offset
    val oldLen = event.oldLength
    val fragment = event.newFragment
    val newLen = event.newLength

    val caret = editor.caretModel.currentCaret

    var markers = getMarkers(caret)

    // if there are changes outside leader
    markers?.let {
      if (!fitsInLeader(it.first(), offset, oldLen)) {
        markers = null
        clearMarkers(caret)
      }
    }


    for (i in 0 until newLen) {
      if (!isValidModelSymbols(fragment[i])) {
        return
      }
    }

    if (markers == null) {
      if (pdm.synchronizer.isInSynchronization(document)) return
      val psiFile = pdm.getPsiFile(document) as? XmlFile ?: return

      val leader = createLeaderMarker(psiFile, document, editor.caretModel.offset) ?: return
      if (!fitsInLeader(leader, offset, oldLen)) return

      leader.rangeMarker.isGreedyToLeft = true
      leader.rangeMarker.isGreedyToRight = true

      if (pdm.isUncommited(document)) {
        pdm.commitDocument(document)
      }
      val supports = findSupports(leader, psiFile, document) ?: return
      setMarkers(caret, leader, supports)
    }
  }

  private fun setMarkers(caret: Caret, leader: SynchronizationData, followers: List<SynchronizationData>?) {
    if (followers == null) {
      clearMarkers(caret)
    }
    else {
      caret.putUserData(MARKERS_KEY, listOf(leader) + followers)
    }
  }

  private fun getMarkers(caret: Caret): List<SynchronizationData>? {
    return caret.getUserData(MARKERS_KEY)
  }

  private fun createLeaderMarker(psiFile: XmlFile, document: Document, offset: Int): SynchronizationData? {
    val projectElement = psiFile.rootTag
    val xmlnsElement = projectElement?.getAttribute("xmlns")?.valueElement
    val schemaElement = projectElement?.getAttribute("xsi:schemaLocation")?.valueElement
    val modelElement = projectElement?.findSubTags("modelVersion")?.firstOrNull()?.children?.filterIsInstance<XmlText>()?.first()
    if (xmlnsElement?.textRange?.contains(offset) == true) return fromXmlns(xmlnsElement, document)
    if (modelElement?.textRange?.contains(offset) == true) return fromModel(modelElement, document)
    if (schemaElement?.textRange?.contains(offset) == true) return fromSchema(schemaElement, document)?.firstOrNull { it.rangeMarker.contains(offset) }
    return null
  }


  private fun clearMarkers(caret: Caret) {
    caret.putUserData(MARKERS_KEY, null)
  }


  private fun findSupports(leader: SynchronizationData, psiFile: XmlFile, document: Document): List<SynchronizationData>? {
    val leaderRange = leader.rangeMarker.textRange
    val leaderElement = psiFile.getViewProvider().findElementAt(leader.rangeMarker.startOffset, XMLLanguage.INSTANCE) ?: return null
    val projectElement = psiFile.rootTag

    val xmlnsElement = projectElement?.getAttribute("xmlns")?.valueElement
    val schemaElement = projectElement?.getAttribute("xsi:schemaLocation")?.valueElement
    val modelElement = projectElement?.findSubTags("modelVersion")?.firstOrNull()?.children?.filterIsInstance<XmlText>()?.first()

    if (!same(leaderElement, xmlnsElement) && !same(leaderElement, schemaElement) && !same(leaderElement, modelElement)) return null
    val result = ArrayList<SynchronizationData>()
    result.addIfNotNull(fromXmlns(xmlnsElement, document))
    fromSchema(schemaElement, document)?.let { result.addAll(it) }
    result.addIfNotNull(fromModel(modelElement, document))
    result.forEach { it.rangeMarker.isGreedyToLeft = true; it.rangeMarker.isGreedyToRight = true }
    return result.filter { it.rangeMarker.isValid }.filterNot { it.rangeMarker.textRange.intersects(leaderRange) }.nullize()
  }

  private fun same(leader: PsiElement?, supporter: XmlElement?): Boolean {
    if (leader == null || supporter == null) return false
    return leader === supporter || leader.parent === supporter
  }

  private fun fromXmlns(xmlnsElement: XmlAttributeValue?, document: Document): SynchronizationData? {
    if (xmlnsElement == null) return null
    val pomRange = findRangeAfter(xmlnsElement, "maven.apache.org/POM/")
    return pomRange?.let { document.createRangeMarker(it).synchronizedDots() }

  }

  private fun fromModel(modelElement: XmlText?, document: Document): SynchronizationData? {
    if (modelElement == null) return null
    return document.createRangeMarker(modelElement.textRange).synchronizedDots()
  }


  private fun fromSchema(schemaElement: XmlAttributeValue?, document: Document): List<SynchronizationData>? {
    if (schemaElement == null) return null
    val result = ArrayList<SynchronizationData>(2)
    val pomRange = findRangeAfter(schemaElement, "maven.apache.org/POM/")
    pomRange?.let { result.add(document.createRangeMarker(it).synchronizedDots()) };

    val xsdRange = findRangeAfter(schemaElement, "maven.apache.org/xsd/maven-", ".xsd")
    xsdRange?.let { result.add(document.createRangeMarker(it).synchronizedDots()) }

    val xsdRangeUnderscores = findRangeAfter(schemaElement, "maven.apache.org/maven-v", ".xsd")
    xsdRangeUnderscores?.let { result.add(document.createRangeMarker(it).synchronizedUnderscore()) }

    return result.nullize()
  }

  private fun findRangeAfter(schemaElement: XmlAttributeValue, prefix: String, end: String = " "): TextRange? {
    val value = schemaElement.value
    val schemaTextRange = schemaElement.valueTextRange

    val foundIndex = value.indexOf(prefix)

    if (foundIndex != -1) {
      val endIdx = getEndIndex(value, foundIndex + prefix.length, end)
      val start = schemaTextRange.startOffset + foundIndex + prefix.length
      val end = schemaTextRange.startOffset + endIdx
      if (start <= end) return TextRange(start, end)
    }
    return null
  }

  private fun getEndIndex(
    value: @NlsSafe String,
    startIndex: Int,
    end: String,
  ): Int {
    val possibleSuffixes = listOf(end, " ", "\n", "\r\n", "\t", ":", "file:", "http:", "https:", ":", "/")
    return possibleSuffixes.map {
      value.indexOf(it, startIndex, true)
    }.map {
      if (it == -1) value.length else it
    }.min()
  }

  private fun isValidModelSymbols(ch: Char): Boolean {
    return ch.isLetterOrDigit() || ch in setOf('.', '_')
  }

  private fun fitsInLeader(leader: SynchronizationData, offset: Int, oldLength: Int): Boolean {
    return leader.rangeMarker.isValid && offset >= leader.rangeMarker.startOffset && (offset + oldLength) <= leader.rangeMarker.endOffset
  }

  fun performReplacement(caret: Caret) {
    val markers = getMarkers(caret) ?: return
    val document: Document = editor.document
    val leader = markers.first()
    val name = getValueToReplace(document, leader)
    if (markers.any { !it.rangeMarker.isValid } || name == null) {
      return
    }
    val apply = Runnable {
      markers.tail().forEach {
        document.replaceString(it.rangeMarker.startOffset, it.rangeMarker.endOffset, it.transformation(name))
      }
    }
    ApplicationManager.getApplication().runWriteAction {
      val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl
      if (lookup != null) {
        lookup.performGuardedChange(apply)
      }
      else {
        apply.run()
      }
    }
  }

  fun beforeCommandFinished() {
    applying = true
    try {
      if (editor.caretModel.isIteratingOverCarets) {
        performReplacement(editor.caretModel.getCurrentCaret())
      }
      else {
        editor.caretModel.runForEachCaret(::performReplacement)
      }
    }
    finally {
      applying = false
    }
  }

  private fun getValueToReplace(document: Document, leader: SynchronizationData): String? {
    if (document.getTextLength() < leader.rangeMarker.getEndOffset()) {
      return null
    }
    return document.getText(leader.rangeMarker.textRange)
  }

}

private fun RangeMarker.synchronizedUnderscore(): SynchronizationData {
  return SynchronizationData(this) {
    it.replace('.', '_').trim()
  }
}

private fun RangeMarker.synchronizedDots(): SynchronizationData {
  return SynchronizationData(this) {
    it.replace('_', '.').trim()
  }
}

private data class SynchronizationData(val rangeMarker: RangeMarker, val transformation: (String) -> String)
