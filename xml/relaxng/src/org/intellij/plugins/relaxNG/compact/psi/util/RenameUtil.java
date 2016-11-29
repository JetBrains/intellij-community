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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncFile;
import org.intellij.plugins.relaxNG.compact.psi.RncGrammar;
import org.jdom.Verifier;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 14.08.2007
 */
public class RenameUtil {

  private static final Set<String> ourRncKeywords = new HashSet<>();

  static {
    Collections.addAll(ourRncKeywords, "attribute", "default", "datatypes", "div", "element", "empty", "external",
                       "grammar", "include", "inherit", "list", "mixed", "namespace", "notAllowed", "parent", "start",
                       "string", "text", "token");
  }

  private RenameUtil() {
  }

  @NotNull
  public static ASTNode createIdentifierNode(PsiManager manager, String name) throws IncorrectOperationException {
    if (isKeyword(name)) {
      name = "\\" + name;
    } else if (!isIdentifier(name)) {
      throw new IncorrectOperationException("Illegal identifier: " + name);
    }

    final PsiFileFactory f = PsiFileFactory.getInstance(manager.getProject());
    final RncFile file = (RncFile)f.createFileFromText("dummy.rnc", RncFileType.getInstance(), name + " = bar");
    final ASTNode astNode = findFirstGrammarNode(file);
    final ASTNode newNode = astNode.findChildByType(RncTokenTypes.IDENTIFIERS);
    assert newNode != null;
    return newNode;
  }

  public static boolean isIdentifier(String name) {
    //return isTokenOfType(manager, name, RncTokenTypes.IDENTIFIER_OR_KEYWORD);
    if (name == null) {
      return false;
    }
    return Verifier.checkXMLName(name) == null ||
           name.length() >= 2 && name.charAt(0) == '\\' && Verifier.checkXMLName(name.substring(1)) == null;
  }

  public static boolean isKeyword(String name) {
    //return isTokenOfType(manager, name, RncTokenTypes.KEYWORDS);
    return ourRncKeywords.contains(name);
  }

  /*private static boolean isTokenOfType(PsiManager manager, String name, TokenSet set) {
    final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(RngCompactLanguage.INSTANCE);
    assert definition != null;
    final Lexer lexer = definition.createLexer(manager.getProject());

    lexer.start(name, 0, name.length(), 0);
    final IElementType t = lexer.getTokenType();
    lexer.advance();
    return lexer.getTokenType() == null && set.contains(t);
  }*/

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
