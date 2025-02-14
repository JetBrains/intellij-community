// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml

import com.intellij.lang.LighterASTNode
import com.intellij.lang.LighterLazyParseableNode
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.html.HTMLParser
import com.intellij.psi.tree.ILazyParseableElementType
import com.intellij.psi.tree.ILightLazyParseableElementType
import com.intellij.util.diff.FlyweightCapableTreeStructure

internal class EmbeddedHtmlContentElementType :
  ILazyParseableElementType("HTML_EMBEDDED_CONTENT", HTMLLanguage.INSTANCE),
  ILightLazyParseableElementType {

  override fun parseContents(
    chameleon: LighterLazyParseableNode,
  ): FlyweightCapableTreeStructure<LighterASTNode> {
    val file = checkNotNull(chameleon.containingFile) { chameleon }
    val builder = PsiBuilderFactory.getInstance().createBuilder(file.project, chameleon)
    HTMLParser().parseWithoutBuildingTree(XmlElementType.HTML_FILE, builder)
    return builder.lightTree
  }
}
