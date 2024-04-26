// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.dtd;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lexer.DtdLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.parsing.xml.DtdParsing;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlEntityDecl;
import org.jetbrains.annotations.NotNull;

public class DTDParserDefinition extends XMLParserDefinition {
  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return LanguageUtil.canStickTokensTogetherByLexer(left, right, new DtdLexer(false));
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new XmlFileImpl(viewProvider, XmlElementType.DTD_FILE);
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new PsiParser() {
      @Override
      public @NotNull ASTNode parse(IElementType root, PsiBuilder builder) {
        return new DtdParsing(root, XmlEntityDecl.EntityContextType.GENERIC_XML, builder).parse();
      }
    };
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return XmlElementType.DTD_FILE;
  }

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new DtdLexer(false);
  }
}
