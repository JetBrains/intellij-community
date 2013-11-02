/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.psi.tree.TokenSet;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Formatter;

/**
 * @author yole
 */
public class PyElementGeneratorImpl extends PyElementGenerator {
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

  public PsiFile createDummyFile(LanguageLevel langLevel, String contents, boolean physical) {
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    final String name = "dummy." + PythonFileType.INSTANCE.getDefaultExtension();
    final LightVirtualFile virtualFile = new LightVirtualFile(name, PythonFileType.INSTANCE, contents);
    virtualFile.putUserData(LanguageLevel.KEY, langLevel);
    final PsiFile psiFile = ((PsiFileFactoryImpl)factory).trySetupPsiForFile(virtualFile, PythonLanguage.getInstance(), physical, true);
    assert psiFile != null;
    return psiFile;
  }

  public PyStringLiteralExpression createStringLiteralAlreadyEscaped(String str) {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a=(" + str + ")");
    final PyAssignmentStatement expressionStatement = (PyAssignmentStatement)dummyFile.getFirstChild();
    return (PyStringLiteralExpression)((PyParenthesizedExpression)expressionStatement.getAssignedValue()).getContainedExpression();
  }


  public PyStringLiteralExpression createStringLiteralFromString(@NotNull String unescaped) {
    return createStringLiteralFromString(null, unescaped);
  }

  public PyStringLiteralExpression createStringLiteral(@NotNull PyStringLiteralExpression oldElement, @NotNull String unescaped) {
    Pair<String, String> quotes = PythonStringUtil.getQuotes(oldElement.getText());
    if (quotes != null) {
      return createStringLiteralAlreadyEscaped(quotes.first + unescaped + quotes.second);
    }
    else {
      return createStringLiteralFromString(unescaped);
    }
  }

  public PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination, @NotNull String unescaped) {
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
      charset = Charset.forName("US-ASCII");
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
        ASTNode next = PyUtil.getNextNonWhitespace(closingTokens[closingTokens.length - 1]);
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

  public PyImportStatement createImportStatementFromText(final LanguageLevel languageLevel,
                                                         final String text) {
    final PsiFile dummyFile = createDummyFile(languageLevel, text);
    return (PyImportStatement)dummyFile.getFirstChild();
  }

  @Override
  public PyImportElement createImportElement(final LanguageLevel languageLevel, String name) {
    return createFromText(languageLevel, PyImportElement.class, "from foo import " + name, new int[]{0, 6});
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
    try {
      //noinspection unchecked
      return (T)ret;
    }
    catch (ClassCastException e) {
      throw new IllegalArgumentException("Can't create an expression of type " + aClass + " from text '" + text + "'");
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
}
