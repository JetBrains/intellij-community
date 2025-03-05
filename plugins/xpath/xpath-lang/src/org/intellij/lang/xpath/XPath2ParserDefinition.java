// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.lang.xpath.psi.impl.*;
import org.jetbrains.annotations.NotNull;

public class XPath2ParserDefinition extends XPathParserDefinition {
  public static final IFileElementType FILE = new IFileElementType("XPATH2_FILE", XPathFileType.XPATH2.getLanguage());

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return XPathLexer.create(true);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new XPath2Parser();
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return TokenSet.create(XPath2TokenTypes.COMMENT);
  }

  @Override
  protected PsiElement createElement(IElementType type, ASTNode node) {
    final PsiElement element = super.createElement(type, node);
    if (element != null) {
      return element;
    }

    if (type == XPath2ElementTypes.VARIABLE_DECL) {
      return new XPath2VariableImpl(node);
    } else if (type == XPath2ElementTypes.CONTEXT_ITEM) {
      return new XPathStepImpl(node);
    } else if (type == XPath2ElementTypes.IF) {
      return new XPath2IfImpl(node);
    } else if (type == XPath2ElementTypes.QUANTIFIED) {
      return new XPath2QuantifiedExprImpl(node);
    } else if (type == XPath2ElementTypes.FOR) {
      return new XPath2ForImpl(node);
    } else if (type == XPath2ElementTypes.BINDING_SEQ) {
      return new XPath2VariableDeclarationImpl(node);
    } else if (type == XPath2ElementTypes.SEQUENCE) {
      return new XPath2SequenceImpl(node);
    } else if (type == XPath2ElementTypes.RANGE_EXPRESSION) {
      return new XPath2RangeExpressionImpl(node);
    } else if (type == XPath2ElementTypes.CASTABLE_AS) {
      return new XPath2CastableImpl(node);
    } else if (type == XPath2ElementTypes.CAST_AS) {
      return new XPath2CastImpl(node);
    } else if (type == XPath2ElementTypes.INSTANCE_OF) {
      return new XPath2InstanceOfImpl(node);
    } else if (type == XPath2ElementTypes.TREAT_AS) {
      return new XPath2TreatAsImpl(node);
    } else if (XPath2ElementTypes.TYPE_ELEMENTS.contains(type)) {
      return new XPath2TypeElementImpl(node);
    }

    return null;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new XPathFile(viewProvider, XPathFileType.XPATH2);
  }
}