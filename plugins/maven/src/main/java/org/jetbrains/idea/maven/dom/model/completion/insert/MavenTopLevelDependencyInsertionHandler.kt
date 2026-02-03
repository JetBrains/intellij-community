// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model.completion.insert

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlText
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.generate.GenerateDependencyProvider
import org.jetbrains.idea.maven.dom.generate.GenerateManagedDependencyAction
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo

internal class MavenTopLevelDependencyInsertionHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    if (TemplateManager.getInstance(context.project).getActiveTemplate(context.editor) != null) {
      return  // Don't brake the template.
    }
    val obj = item.getObject()
    if (obj !is MavenRepositoryArtifactInfo) {
      return
    }
    val contextFile = context.file
    if (contextFile !is XmlFile) return
    val project = context.project
    val model = MavenDomUtil.getMavenDomModel(contextFile, MavenDomProjectModel::class.java)
    if (model == null) {
      return
    }
    val managedDependencies = GenerateManagedDependencyAction.collectManagingDependencies(model)
    var element = contextFile.findElementAt(context.startOffset)
    if (element !is XmlText) {
      element = PsiTreeUtil.getParentOfType(element, XmlText::class.java)
      if (element == null) {
        return
      }
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val dependency = GenerateDependencyProvider.createDependency(model, context.editor, managedDependencies, listOf(obj))

    element.delete()
    if (dependency != null && dependency.getXmlTag() != null) {
      context.editor.getCaretModel().moveToOffset(dependency.getXmlTag()!!.getTextOffset())
    }
    context.commitDocument()

    logMavenDependencyInsertion(context, item, obj)
  }

  companion object {
    val INSTANCE: InsertHandler<LookupElement> = MavenTopLevelDependencyInsertionHandler()
  }
}
