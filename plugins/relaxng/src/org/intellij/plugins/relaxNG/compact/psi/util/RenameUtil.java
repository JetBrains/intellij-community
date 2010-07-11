/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.psi.util;

import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.RngCompactLanguage;
import org.intellij.plugins.relaxNG.compact.psi.RncFile;
import org.intellij.plugins.relaxNG.compact.psi.RncGrammar;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 14.08.2007
 */
public class RenameUtil {
  @NotNull
  public static ASTNode createIdentifierNode(PsiManager manager, String name) throws IncorrectOperationException {
    if (isKeyword(manager, name)) {
      name = "\\" + name;
    } else if (!isIdentifier(manager, name)) {
      throw new IncorrectOperationException("Illegal identifier: " + name);
    }

    final PsiFileFactory f = PsiFileFactory.getInstance(manager.getProject());
    final RncFile file = (RncFile)f.createFileFromText("dummy.rnc", RncFileType.getInstance(), name + " = bar");
    final ASTNode astNode = findFirstGrammarNode(file);
    final ASTNode newNode = astNode.findChildByType(RncTokenTypes.IDENTIFIERS);
    assert newNode != null;
    return newNode;
  }

  public static boolean isIdentifier(PsiManager manager, String name) {
    return isTokenOfType(manager, name, RncTokenTypes.IDENTIFIER_OR_KEYWORD);
  }

  public static boolean isKeyword(PsiManager manager, String name) {
    return isTokenOfType(manager, name, RncTokenTypes.KEYWORDS);
  }

  private static boolean isTokenOfType(PsiManager manager, String name, TokenSet set) {
    final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(RngCompactLanguage.INSTANCE);
    assert definition != null;
    final Lexer lexer = definition.createLexer(manager.getProject());

    lexer.start(name, 0, name.length(), 0);
    final IElementType t = lexer.getTokenType();
    lexer.advance();
    return lexer.getTokenType() == null && set.contains(t);
  }

  public static ASTNode createPrefixedNode(PsiManager manager, String prefix, String localPart) {
    final PsiFileFactory f = PsiFileFactory.getInstance(manager.getProject());
    final RncFile file = (RncFile)f.createFileFromText("dummy.rnc", RncFileType.getInstance(), "element " + prefix + ":" + localPart + " { text }");

    final ASTNode node = findFirstGrammarNode(file);
    final ASTNode nameClassNode = node.findChildByType(RncElementTypes.NAME_CLASS);
    assert nameClassNode != null;

    final ASTNode astNode = nameClassNode.findChildByType(RncElementTypes.NAME);
    assert astNode != null;
    return astNode;
  }

  @NotNull
  private static ASTNode findFirstGrammarNode(RncFile file) {
    final RncGrammar grammar = file.getGrammar();
    assert grammar != null;
    final ASTNode grammarNode = grammar.getNode();
    assert grammarNode != null;
    final ASTNode astNode = grammarNode.getFirstChildNode();
    assert astNode != null;
    return astNode;
  }

  public static ASTNode createLiteralNode(PsiManager manager, String content) {
    final PsiFileFactory f = PsiFileFactory.getInstance(manager.getProject());
    final RncFile file = (RncFile)f.createFileFromText("dummy.rnc", RncFileType.getInstance(), "include \"" + content + "\"");

    final ASTNode include = findFirstGrammarNode(file);
    final ASTNode literal = include.findChildByType(RncTokenTypes.LITERAL);
    assert literal != null;
    return literal;
  }
}
