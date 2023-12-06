// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xhtml;

import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XHtmlLexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;

public class XHTMLParserDefinition extends XMLParserDefinition {

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new XHtmlLexer();
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new XmlFileImpl(viewProvider, XmlElementType.XHTML_FILE);
  }

}
