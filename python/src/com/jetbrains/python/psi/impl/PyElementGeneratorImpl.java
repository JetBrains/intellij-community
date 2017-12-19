/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Collections2;
import com.google.common.collect.Queues;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Deque;
import java.util.Formatter;

/**
 * @author yole
 */
public class PyElementGeneratorImpl extends PyElementGenerator {
  private static final CommasOnly COMMAS_ONLY = new CommasOnly();
  private final Project myProject;

  public PyElementGeneratorImpl(Project project) {
    myProject = project;
  }

  public ASTNode createNameIdentifier(String name, LanguageLevel languageLevel) {
    final PsiFile dummyFile = createDummyFile(languageLevel, name);
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    final PyReferenceExpression refExpression = (PyReferenceExpression)expressionStatement.getFirstChild();

    return refExpression.getNode().getFirstChildNode();
  }

  @Override
  public PsiFile createDummyFile(LanguageLevel langLevel, String contents) {
    return createDummyFile(langLevel, contents, false);
  }

  /**
   * TODO: Use {@link PsiFileFactory} instead?
   */
  public PsiFile createDummyFile(LanguageLevel langLevel, String contents, boolean physical) {
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    final String name = getDummyFileName();
    final LightVirtualFile virtualFile = new LightVirtualFile(name, PythonFileType.INSTANCE, contents);
    virtualFile.putUserData(LanguageLevel.KEY, langLevel);
    final PsiFile psiFile = ((PsiFileFactoryImpl)factory).trySetupPsiForFile(virtualFile, PythonLanguage.getInstance(), physical, true);
    assert psiFile != null;
    return psiFile;
  }

  /**
   * @return name used for {@link #createDummyFile(LanguageLevel, String)}
   */
  @NotNull
  public static String getDummyFileName() {
    return "dummy." + PythonFileType.INSTANCE.getDefaultExtension();
  }

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
    return createStringLiteralFromString(null, unescaped, true);
  }

  @Override
  public PyStringLiteralExpression createStringLiteral(@NotNull StringLiteralExpression oldElement, @NotNull String unescaped) {
    Pair<String, String> quotes = PyStringLiteralUtil.getQuotes(oldElement.getText());
    if (quotes != null) {
      return createStringLiteralAlreadyEscaped(quotes.first + unescaped + quotes.second);
    }
    else {
      return createStringLiteralFromString(unescaped);
    }
  }


  @Override
  public PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination,
                                                                 @NotNull String unescaped,
                                                                 final boolean preferUTF8) {
    boolean useDouble = !unescaped.contains("\"");
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
      charset = (preferUTF8 ? CharsetToolkit.UTF8_CHARSET : Charset.forName("US-ASCII"));
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
        if (c == '\r') {
          buf.append("\\r");
        }
        else if (c == '\n') buf.append("\\n");
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

  public PyListLiteralExpression createListLiteral() {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "[]");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    return (PyListLiteralExpression)expressionStatement.getFirstChild();
  }

  public ASTNode createComma() {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "[0,]");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    ASTNode zero = expressionStatement.getFirstChild().getNode().getFirstChildNode().getTreeNext();
    return zero.getTreeNext().copyElement();
  }

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

  public PyBinaryExpression createBinaryExpression(String s, PyExpression expr, PyExpression listLiteral) {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a " + s + " b");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    PyBinaryExpression binExpr = (PyBinaryExpression)expressionStatement.getExpression();
    ASTNode binnode = binExpr.getNode();
    binnode.replaceChild(binExpr.getLeftExpression().getNode(), expr.getNode().copyElement());
    binnode.replaceChild(binExpr.getRightExpression().getNode(), listLiteral.getNode().copyElement());
    return binExpr;
  }

  public PyExpression createExpressionFromText(final String text) {
    return createExpressionFromText(LanguageLevel.getDefault(), text);
  }

  @NotNull
  public PyExpression createExpressionFromText(final LanguageLevel languageLevel, final String text) {
    final PsiFile dummyFile = createDummyFile(languageLevel, text);
    final PsiElement element = dummyFile.getFirstChild();
    if (element instanceof PyExpressionStatement) {
      return ((PyExpressionStatement)element).getExpression();
    }
    throw new IncorrectOperationException("could not parse text as expression: " + text);
  }

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

  static final int[] FROM_ROOT = new int[]{0};

  @NotNull
  public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text) {
    return createFromText(langLevel, aClass, text, FROM_ROOT);
  }

  @NotNull
  @Override
  public <T> T createPhysicalFromText(LanguageLevel langLevel, Class<T> aClass, String text) {
    return createFromText(langLevel, aClass, text, FROM_ROOT, true);
  }

  static int[] PATH_PARAMETER = {0, 3, 1};

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

  @NotNull
  public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text, final int[] path) {
    return createFromText(langLevel, aClass, text, path, false);
  }

  @NotNull
  public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text, final int[] path, boolean physical) {
    PsiElement ret = createDummyFile(langLevel, text, physical);
    for (int skip : path) {
      if (ret != null) {
        ret = ret.getFirstChild();
        for (int i = 0; i < skip; i += 1) {
          if (ret != null) {
            ret = ret.getNextSibling();
          }
          else {
            ret = null;
            break;
          }
        }
      }
      else {
        break;
      }
    }
    if (ret == null) {
      throw new IllegalArgumentException("Can't find element matching path " + Arrays.toString(path) + " in text '" + text + "'");
    }
    if (aClass.isInstance(ret)) {
      //noinspection unchecked
      return (T)ret;
    }
    else {
      throw new IllegalArgumentException("Can't create an element of type " + aClass + " from text '" + text + "', got " + ret.getClass() + " instead");
    }
  }

  @Override
  public PyPassStatement createPassStatement() {
    final PyStatementList statementList = createPassStatementList();
    return (PyPassStatement)statementList.getStatements()[0];
  }

  @NotNull
  @Override
  public PyDecoratorList createDecoratorList(@NotNull final String... decoratorTexts) {
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

  public PyExpressionStatement createDocstring(String content) {
    return createFromText(LanguageLevel.getDefault(),
                          PyExpressionStatement.class, content + "\n");
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

  private static class CommasOnly extends NotNullPredicate<LeafPsiElement> {
    @Override
    protected boolean applyNotNull(@NotNull final LeafPsiElement input) {
      return input.getNode().getElementType().equals(PyTokenTypes.COMMA);
    }
  }
}
