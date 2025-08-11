// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion.insert

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil.invokeCompletion
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo

class MavenArtifactIdInsertionHandler : MavenDependencyInsertionHandler() {
  override fun setDependency(
    context: InsertionContext,
    completionItem: MavenRepositoryArtifactInfo,
    contextFile: XmlFile?, domCoordinates: MavenDomShortArtifactCoordinates
  ) {
    domCoordinates.getArtifactId().setStringValue(completionItem.getArtifactId())
    val tag = domCoordinates.getGroupId().getXmlTag()
    if (tag == null) {
      domCoordinates.getGroupId().setStringValue("")
    }
    val position = domCoordinates.getGroupId().getXmlTag()!!.getValue().getTextRange().startOffset
    context.editor.getCaretModel().moveToOffset(position)
    invokeCompletion(context, CompletionType.BASIC)
  }

  companion object {
    val INSTANCE: InsertHandler<LookupElement?> = MavenArtifactIdInsertionHandler()
  }
}
