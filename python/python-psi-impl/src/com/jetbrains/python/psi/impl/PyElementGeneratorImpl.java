// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Collections2;
import com.google.common.collect.Queues;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.NotNullPredicate;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Deque;
import java.util.Formatter;


public final class PyElementGeneratorImpl extends PyElementGenerator {
  private static final CommasOnly COMMAS_ONLY = new CommasOnly();

  public PyElementGeneratorImpl(Project project) {
    super(project);
  }

  @Override
  protected void specifyFileLanguageLevel(@NotNull VirtualFile virtualFile, @Nullable LanguageLevel langLevel) {
    PythonLanguageLevelPusher.specifyFileLanguageLevel(virtualFile, langLevel);
  }

  @Override
  public ASTNode createNameIdentifier(String name, LanguageLevel languageLevel) {
    final PsiFile dummyFile = createDummyFile(languageLevel, name);
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    final PyReferenceExpression refExpression = (PyReferenceExpression)expressionStatement.getFirstChild();

    return refExpression.getNode().getFirstChildNode();
  }

  @Override
  public PyStringLiteralExpression createStringLiteralAlreadyEscaped(String str) {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a=(" + str + ")");
    final PyAssignmentStatement expressionStatement = (PyAssignmentStatement)dummyFile.getFirstChild();
    final PyExpression assignedValue = expressionStatement.getAssignedValue();
    if (assignedValue != null) {
      return (PyStringLiteralExpression)((PyParenthesizedExpression)assignedValue).getContainedExpression();
    }
    return createStringLiteralFromString(str);
  }


  @Override
  public PyStringLiteralExpression createStringLiteralFromString(@NotNull String unescaped) {
    return createStringLiteralFromString(null, unescaped, true, true);
  }

  @Override
  public PyStringLiteralExpression createStringLiteral(@NotNull StringLiteralExpression oldElement, @NotNull String unescaped) {
    Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(oldElement.getText());
    if (quotes != null) {
      return createStringLiteralAlreadyEscaped(quotes.first + unescaped + quotes.second);
    }
    else {
      return createStringLiteralFromString(unescaped);
    }
  }


  @Override
  protected PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination,
                                                                 @NotNull String unescaped,
                                                                 final boolean preferUTF8,
                                                                 boolean preferDoubleQuotes) {
    boolean useDouble = (!unescaped.contains("\"") && preferDoubleQuotes) || unescaped.contains("'");
    boolean useMulti = unescaped.matches(".*(\r|\n).*");
    String quotes;
    if (useMulti) {
      quotes = useDouble ? "\"\"\"" : "'''";
    }
    else {
      quotes = useDouble ? "\"" : "'";
    }
    StringBuilder buf = new StringBuilder(unescaped.length() * 2);
    buf.append(quotes);
    VirtualFile vfile = destination == null ? null : destination.getVirtualFile();
    Charset charset;
    if (vfile == null) {
      charset = (preferUTF8 ? StandardCharsets.UTF_8 : StandardCharsets.US_ASCII);
    }
    else {
      charset = vfile.getCharset();
    }
    CharsetEncoder encoder = charset.newEncoder();
    Formatter formatter = new Formatter(buf);
    boolean unicode = false;
    for (int i = 0; i < unescaped.length(); i++) {
      int c = unescaped.codePointAt(i);
      if (c == '"' && useDouble) {
        buf.append("\\\"");
      }
      else if (c == '\'' && !useDouble) {
        buf.append("\\'");
      }
      else if ((c == '\r' || c == '\n') && !useMulti) {
        buf.append(c == '\r' ? "\\r" : "\\n");
      }
      else if (!encoder.canEncode(new String(Character.toChars(c)))) {
        if (c <= 0xff) {
          formatter.format("\\x%02x", c);
        }
        else if (c < 0xffff) {
          unicode = true;
          formatter.format("\\u%04x", c);
        }
        else {
          unicode = true;
          formatter.format("\\U%08x", c);
        }
      }
      else {
        buf.appendCodePoint(c);
      }
    }
    buf.append(quotes);
    if (unicode) buf.insert(0, "u");

    return createStringLiteralAlreadyEscaped(buf.toString());
  }

  @Override
  public PyStringLiteralExpression createStringLiteralFromString(@NotNull String unescaped, boolean preferDoubleQuotes) {
    return createStringLiteralFromString(null, unescaped, true, preferDoubleQuotes);
  }

  @Override
  public PyListLiteralExpression createListLiteral() {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "[]");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    return (PyListLiteralExpression)expressionStatement.getFirstChild();
  }

  @Override
  public ASTNode createDot() {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a.b");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    ASTNode dot = expressionStatement.getFirstChild().getNode().getFirstChildNode().getTreeNext();
    return dot.copyElement();
  }

  @Override
  @NotNull
  public PsiElement insertItemIntoListRemoveRedundantCommas(
    @NotNull final PyElement list,
    @Nullable final PyExpression afterThis,
    @NotNull final PyExpression toInsert) {
    // TODO: #insertItemIntoList is probably buggy. In such case, fix it and get rid of this method
    final PsiElement result = insertItemIntoList(list, afterThis, toInsert);
    final LeafPsiElement[] leafs = PsiTreeUtil.getChildrenOfType(list, LeafPsiElement.class);
    if (leafs != null) {
      final Deque<LeafPsiElement> commas = Queues.newArrayDeque(Collections2.filter(Arrays.asList(leafs), COMMAS_ONLY));
      if (!commas.isEmpty()) {
        final LeafPsiElement lastComma = commas.getLast();
        if (PsiTreeUtil.getNextSiblingOfType(lastComma, PyExpression.class) == null) { //Comma has no expression after it
          lastComma.delete();
        }
      }
    }

    return result;
  }

  // TODO: Adds comma to empty list: adding "foo" to () will create (foo,). That is why "insertItemIntoListRemoveRedundantCommas" was created.
  // We probably need to fix this method and delete insertItemIntoListRemoveRedundantCommas
  @Override
  public PsiElement insertItemIntoList(PyElement list, @Nullable PyExpression afterThis, PyExpression toInsert)
    throws IncorrectOperationException {
    ASTNode add = toInsert.getNode().copyElement();
    if (afterThis == null) {
      ASTNode exprNode = list.getNode();
      ASTNode[] closingTokens = exprNode.getChildren(TokenSet.create(PyTokenTypes.LBRACKET, PyTokenTypes.LPAR));
      if (closingTokens.length == 0) {
        // we tried our best. let's just insert it at the end
        exprNode.addChild(add);
      }
      else {
        ASTNode next = PyPsiUtils.getNextNonWhitespaceSibling(closingTokens[closingTokens.length - 1]);
        if (next != null) {
          ASTNode comma = createComma();
          exprNode.addChild(comma, next);
          exprNode.addChild(add, comma);
        }
        else {
          exprNode.addChild(add);
        }
      }
    }
    else {
      ASTNode lastArgNode = afterThis.getNode();
      ASTNode comma = createComma();
      ASTNode parent = lastArgNode.getTreeParent();
      ASTNode afterLast = lastArgNode.getTreeNext();
      if (afterLast == null) {
        parent.addChild(add);
      }
      else {
        parent.addChild(add, afterLast);
      }
      parent.addChild(comma, add);
    }
    return add.getPsi();
  }

  @Override
  public PyBinaryExpression createBinaryExpression(String s, PyExpression expr, PyExpression listLiteral) {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a " + s + " b");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    PyBinaryExpression binExpr = (PyBinaryExpression)expressionStatement.getExpression();
    ASTNode binnode = binExpr.getNode();
    binnode.replaceChild(binExpr.getLeftExpression().getNode(), expr.getNode().copyElement());
    binnode.replaceChild(binExpr.getRightExpression().getNode(), listLiteral.getNode().copyElement());
    return binExpr;
  }

  @Override
  @NotNull
  public PyExpression createExpressionFromText(@NotNull LanguageLevel languageLevel, @NotNull String text) {
    final PsiFile dummyFile = createDummyFile(languageLevel, text);
    final PsiElement element = dummyFile.getFirstChild();
    if (element instanceof PyExpressionStatement) {
      return ((PyExpressionStatement)element).getExpression();
    }
    throw new IncorrectOperationException("could not parse text as expression: " + text);
  }

  @Override
  public @NotNull PyPattern createPatternFromText(@NotNull LanguageLevel languageLevel, @NotNull String text)
    throws IncorrectOperationException {
    String matchStatement = "match x:\n" +
                            "    case C(" + text + "):\n" +
                            "        pass ";
    int[] pathToAttrPattern = {0, 5, 2, 1, 1};
    return createFromText(languageLevel, PyPattern.class, matchStatement, pathToAttrPattern);
  }

  @Override
  @NotNull
  public PyCallExpression createCallExpression(final LanguageLevel langLevel, String functionName) {
    final PsiFile dummyFile = createDummyFile(langLevel, functionName + "()");
    final PsiElement child = dummyFile.getFirstChild();
    if (child != null) {
      final PsiElement element = child.getFirstChild();
      if (element instanceof PyCallExpression) {
        return (PyCallExpression)element;
      }
    }
    throw new IllegalArgumentException("Invalid call expression text " + functionName);
  }

  @Override
  public PyImportElement createImportElement(@NotNull final LanguageLevel languageLevel, @NotNull String name, @Nullable String alias) {
    final String importStatement = "from foo import " + name + (alias != null ? " as " + alias : "");
    return createFromText(languageLevel, PyImportElement.class, importStatement, new int[]{0, 6});
  }

  @Override
  public PyFunction createProperty(LanguageLevel languageLevel,
                                   String propertyName,
                                   String fieldName,
                                   AccessDirection accessDirection) {
    String propertyText;
    if (accessDirection == AccessDirection.DELETE) {
      propertyText = "@" + propertyName + ".deleter\ndef " + propertyName + "(self):\n  del self." + fieldName;
    }
    else if (accessDirection == AccessDirection.WRITE) {
      propertyText = "@" + propertyName + ".setter\ndef " + propertyName + "(self, value):\n  self." + fieldName + " = value";
    }
    else {
      propertyText = "@property\ndef " + propertyName + "(self):\n  return self." + fieldName;
    }
    return createFromText(languageLevel, PyFunction.class, propertyText);
  }

  static int[] PATH_PARAMETER = {0, 3, 1};

  @Override
  public PyNamedParameter createParameter(@NotNull String name) {
    return createParameter(name, null, null, LanguageLevel.getDefault());
  }

  @NotNull
  @Override
  public PyParameterList createParameterList(@NotNull LanguageLevel languageLevel, @NotNull String text) {
    return createFromText(languageLevel, PyParameterList.class, "def f" + text + ": pass", new int[]{0, 3});
  }

  @NotNull
  @Override
  public PyArgumentList createArgumentList(@NotNull LanguageLevel languageLevel, @NotNull String text) {
    return createFromText(languageLevel, PyArgumentList.class, "f" + text, new int[]{0, 0, 1});
  }


  @Override
  public PyNamedParameter createParameter(@NotNull String name, @Nullable String defaultValue, @Nullable String annotation,
                                          @NotNull LanguageLevel languageLevel) {
    String parameterText = name;
    if (annotation != null) {
      parameterText += ": " + annotation;
    }
    if (defaultValue != null) {
      parameterText += " = " + defaultValue;
    }

    return createFromText(languageLevel, PyNamedParameter.class, "def f(" + parameterText + "): pass", PATH_PARAMETER);
  }

  @Override
  public PyKeywordArgument createKeywordArgument(LanguageLevel languageLevel, String keyword, String value) {
    PyCallExpression callExpression = (PyCallExpression)createExpressionFromText(languageLevel, "foo(" + keyword + "=" + value + ")");
    return (PyKeywordArgument)callExpression.getArguments()[0];
  }

  @Override
  public PyPassStatement createPassStatement() {
    final PyStatementList statementList = createPassStatementList();
    return (PyPassStatement)statementList.getStatements()[0];
  }

  @NotNull
  @Override
  public PyDecoratorList createDecoratorList(final String @NotNull ... decoratorTexts) {
    assert decoratorTexts.length > 0;
    StringBuilder functionText = new StringBuilder();
    for (String decoText : decoratorTexts) {
      functionText.append(decoText).append("\n");
    }
    functionText.append("def foo():\n\tpass");
    final PyFunction function = createFromText(LanguageLevel.getDefault(), PyFunction.class,
                                               functionText.toString());
    final PyDecoratorList decoratorList = function.getDecoratorList();
    assert decoratorList != null;
    return decoratorList;
  }

  private PyStatementList createPassStatementList() {
    final PyFunction function = createFromText(LanguageLevel.getDefault(), PyFunction.class, "def foo():\n\tpass");
    return function.getStatementList();
  }

  @NotNull
  @Override
  public PsiElement createNewLine() {
    return createFromText(LanguageLevel.getDefault(), PsiWhiteSpace.class, " \n\n ");
  }

  @NotNull
  @Override
  public PyFromImportStatement createFromImportStatement(@NotNull LanguageLevel languageLevel, @NotNull String qualifier,
                                                         @NotNull String name, @Nullable String alias) {
    final String asClause = StringUtil.isNotEmpty(alias) ? " as " + alias : "";
    final String statement = "from " + qualifier + " import " + name + asClause;
    return createFromText(languageLevel, PyFromImportStatement.class, statement);
  }

  @NotNull
  @Override
  public PyImportStatement createImportStatement(@NotNull LanguageLevel languageLevel, @NotNull String name, @Nullable String alias) {
    final String asClause = StringUtil.isNotEmpty(alias) ? " as " + alias : "";
    final String statement = "import " + name + asClause;
    return createFromText(languageLevel, PyImportStatement.class, statement);
  }

  @NotNull
  @Override
  public PyNoneLiteralExpression createEllipsis() {
    return createFromText(LanguageLevel.PYTHON30, PyNoneLiteralExpression.class, "...", new int[]{0, 0});
  }

  @NotNull
  @Override
  public PySingleStarParameter createSingleStarParameter() {
    return createFromText(LanguageLevel.PYTHON30, PySingleStarParameter.class, "def foo(*): pass", new int[]{0, 3, 1});
  }

  private static class CommasOnly extends NotNullPredicate<LeafPsiElement> {
    @Override
    protected boolean applyNotNull(@NotNull final LeafPsiElement input) {
      return input.getNode().getElementType().equals(PyTokenTypes.COMMA);
    }
  }
}
