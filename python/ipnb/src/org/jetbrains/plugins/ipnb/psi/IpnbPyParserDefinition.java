// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IFileElementType;
import com.jetbrains.python.PythonParserDefinition;
import com.jetbrains.python.console.parsing.PythonConsoleLexer;
import org.jetbrains.annotations.NotNull;

public class IpnbPyParserDefinition extends PythonParserDefinition {
  public static final IFileElementType IPNB_PYTHON_FILE = new IpnbPyFileElementType(IpnbPyLanguageDialect.getInstance());

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new PythonConsoleLexer();
  }

  @NotNull
  @Override
  public PsiParser createParser(Project project) {
    return new IpnbPyParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return IPNB_PYTHON_FILE;
  }
}
