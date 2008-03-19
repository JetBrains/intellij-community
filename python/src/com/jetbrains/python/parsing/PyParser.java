/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.parsing;

import com.intellij.lang.PsiParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.05.2005
 * Time: 10:06:10
 * To change this template use File | Settings | File Templates.
 */
public class PyParser implements PsiParser {
  private static final Logger LOGGER = Logger.getInstance(PyParser.class.getName());

  public
  @NotNull
  ASTNode parse(IElementType root, PsiBuilder builder) {
    builder.setDebugMode(false);
    long start = System.currentTimeMillis();
    final PsiBuilder.Marker rootMarker = builder.mark();
    ParsingContext context = new ParsingContext(builder);
    while (!builder.eof()) {
      context.getStatementParser().parseStatement();
    }
    rootMarker.done(root);
    ASTNode ast = builder.getTreeBuilt();
    long diff = System.currentTimeMillis() - start;
    double kb = builder.getCurrentOffset() / 1000.0;
    LOGGER.debug("Parsed " + String.format("%.1f", kb) + "K file in " + diff + "ms");
    return ast;
  }
}
