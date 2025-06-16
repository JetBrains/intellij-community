// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.parsing.xml

import com.intellij.lang.*
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTokenType
import com.intellij.util.ThreeState
import com.intellij.util.TripleFunction
import com.intellij.util.diff.FlyweightCapableTreeStructure

open class XmlParser : PsiParser, LightPsiParser {
  override fun parse(
    root: IElementType,
    builder: PsiBuilder,
  ): ASTNode {
    parseLight(root, builder)
    return builder.treeBuilt
  }

  override fun parseLight(
    root: IElementType,
    builder: PsiBuilder,
  ) {
    builder.enforceCommentTokens(TokenSet.EMPTY)
    builder.putUserData(PsiBuilderImpl.CUSTOM_COMPARATOR, REPARSE_XML_TAG_BY_NAME)
    val file = builder.mark()
    XmlParsing(builder).parseDocument()
    file.done(root)
  }
}

// tries to match an old and new XmlTag by name
private val REPARSE_XML_TAG_BY_NAME: TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState> =
  TripleFunction(::reparseXmlTagByName)

private fun reparseXmlTagByName(
  oldNode: ASTNode,
  newNode: LighterASTNode,
  structure: FlyweightCapableTreeStructure<LighterASTNode>,
): ThreeState {
  if (oldNode !is PsiNamedElement || oldNode.elementType !== XmlElementType.XML_TAG || newNode.tokenType !== XmlElementType.XML_TAG) {
    return ThreeState.UNSURE
  }

  val oldName = oldNode.name
  val childrenRef = Ref.create<Array<LighterASTNode>>()
  val count = structure.getChildren(newNode, childrenRef)

  if (count < 3) {
    return ThreeState.UNSURE
  }

  val children = childrenRef.get()!!
  if (children[0].tokenType !== XmlTokenType.XML_START_TAG_START) {
    return ThreeState.UNSURE
  }
  if (children[1].tokenType !== XmlTokenType.XML_NAME) {
    return ThreeState.UNSURE
  }
  if (children[2].tokenType !== XmlTokenType.XML_TAG_END) {
    return ThreeState.UNSURE
  }

  val name = children[1] as LighterASTTokenNode
  val newName = name.text

  // note: oldName is String, newName is CharSequence, so plain kotlin `==` can't be used!
  if (Comparing.equal(oldName, newName)) {
    return ThreeState.UNSURE
  }

  // different names => oldNode and newNode are not equal
  return ThreeState.NO
}
