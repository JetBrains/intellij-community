// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model.completion.insert

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil.findManagedDependency
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil.invokeCompletion
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil.isInsideManagedDependency
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.MavenDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.onlinecompletion.MavenScopeTable
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo

open class MavenDependencyInsertionHandler : InsertHandler<LookupElement?> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    /*if (TemplateManager.getInstance(context.getProject()).getActiveTemplate(context.getEditor()) != null) {
      return; // Don't brake the template.
    }*/
    val obj = item.getObject()
    if (obj !is MavenRepositoryArtifactInfo) {
      return
    }
    val contextFile = context.file
    if (contextFile !is XmlFile) return
    val element = contextFile.findElementAt(context.startOffset)
    val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java)
    if (tag == null) {
      return
    }
    context.commitDocument()
    val domCoordinates: MavenDomShortArtifactCoordinates? = getDomCoordinatesFromCurrentTag(context, tag)
    if (domCoordinates == null) {
      return
    }
    setDependency(context, obj, contextFile, domCoordinates)

    logMavenDependencyInsertion(context, item, obj)
  }


  protected open fun setDependency(
    context: InsertionContext,
    completionItem: MavenRepositoryArtifactInfo,
    contextFile: XmlFile?, domCoordinates: MavenDomShortArtifactCoordinates
  ) {
    domCoordinates.getGroupId().setStringValue(completionItem.getGroupId())

    domCoordinates.getArtifactId().setStringValue(completionItem.getArtifactId())

    if (domCoordinates is MavenDomDependency) {
      val scope = MavenScopeTable.getUsualScope(completionItem)
      if (scope != null) {
        domCoordinates.getScope().setStringValue(scope)
      }
    }

    val domModel =
      DomManager.getDomManager(context.project).getFileElement(contextFile, MavenDomProjectModel::class.java)


    if (!isInsideManagedDependency(domCoordinates)) {
      val declarationOfDependency = findManagedDependency(domModel!!.rootElement, context.project, completionItem.groupId, completionItem.artifactId)
      if (declarationOfDependency != null) {
        if (domCoordinates is MavenDomDependency) {
          if (declarationOfDependency.type.rawText != null) {
            domCoordinates.type.stringValue = declarationOfDependency.type.rawText
          }
          if (declarationOfDependency.classifier.rawText != null) {
            domCoordinates.classifier.stringValue = declarationOfDependency.classifier.rawText
          }
        }
        return
      }
    }

    if (domCoordinates is MavenDomArtifactCoordinates) {
      insertVersion(context, completionItem, domCoordinates)
    }
  }

  companion object {
    val INSTANCE: InsertHandler<LookupElement?> = MavenDependencyInsertionHandler()

    private fun getDomCoordinatesFromCurrentTag(context: InsertionContext, tag: XmlTag): MavenDomShortArtifactCoordinates? {
      var element = DomManager.getDomManager(context.project).getDomElement(tag)
      //todo: show notification
      if (element is MavenDomShortArtifactCoordinates) {
        tag.getValue().setText("")
        return element
      }
      //try parent
      element = DomManager.getDomManager(context.project).getDomElement(tag.parentTag)
      if (element is MavenDomShortArtifactCoordinates) {
        return element
      }

      return null
    }

    private fun insertVersion(
      context: InsertionContext,
      completionItem: MavenRepositoryArtifactInfo,
      domCoordinates: MavenDomArtifactCoordinates
    ) {
      if (completionItem.items.size == 1 && completionItem.version != null) {
        domCoordinates.getVersion().setStringValue(completionItem.version)
      }
      else {
        domCoordinates.version.stringValue = ""

        val versionPosition = domCoordinates.version.xmlTag!!.value.textRange.startOffset

        context.editor.caretModel.moveToOffset(versionPosition)

        invokeCompletion(context, CompletionType.BASIC)
      }
    }
  }
}
