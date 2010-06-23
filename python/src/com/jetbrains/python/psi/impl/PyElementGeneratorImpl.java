package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Formatter;

/**
 * @author yole
 */
public class PyElementGeneratorImpl extends PyElementGenerator {
  private final Project myProject;

  public PyElementGeneratorImpl(Project project) {
    myProject = project;
  }

  public ASTNode createNameIdentifier(String name) {
    final PsiFile dummyFile = createDummyFile(name);
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    final PyReferenceExpression refExpression = (PyReferenceExpression)expressionStatement.getFirstChild();

    return refExpression.getNode().getFirstChildNode();
  }

  @Override
  public PsiFile createDummyFile(String contents) {
    return PsiFileFactory.getInstance(myProject).createFileFromText("dummy." + PythonFileType.INSTANCE.getDefaultExtension(), contents);
  }

  public PyStringLiteralExpression createStringLiteralAlreadyEscaped(String str) {
    final PsiFile dummyFile = createDummyFile(str);
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    return (PyStringLiteralExpression)expressionStatement.getFirstChild();
  }

  public PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination, String unescaped) {
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
    final PsiFile dummyFile = createDummyFile("[]");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    return (PyListLiteralExpression)expressionStatement.getFirstChild();
  }

  public ASTNode createComma() {
    final PsiFile dummyFile = createDummyFile("[0,]");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    ASTNode zero = expressionStatement.getFirstChild().getNode().getFirstChildNode().getTreeNext();
    return zero.getTreeNext().copyElement();
  }

  public ASTNode createDot() {
    final PsiFile dummyFile = createDummyFile("a.b");
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
    final PsiFile dummyFile = createDummyFile("a " + s + " b");
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    PyBinaryExpression binExpr = (PyBinaryExpression)expressionStatement.getExpression();
    ASTNode binnode = binExpr.getNode();
    binnode.replaceChild(binExpr.getLeftExpression().getNode(), expr.getNode().copyElement());
    binnode.replaceChild(binExpr.getRightExpression().getNode(), listLiteral.getNode().copyElement());
    return binExpr;
  }

  public PyExpression createExpressionFromText(final String text) {
    final PsiFile dummyFile = createDummyFile(text);
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    return expressionStatement.getExpression();
  }

  public PyCallExpression createCallExpression(String functionName) {
    final PsiFile dummyFile = createDummyFile(functionName + "()");
    return (PyCallExpression)dummyFile.getFirstChild().getFirstChild();
  }

  public PyImportStatement createImportStatementFromText(final String text) {
    final PsiFile dummyFile = createDummyFile(text);
    return (PyImportStatement)dummyFile.getFirstChild();
  }

  @Override
  public PyImportElement createImportElement(String name) {
    return createFromText(PyImportElement.class, "from foo import " + name, new int[]{0, 6});
  }

  static final int[] FROM_ROOT = new int[]{0};

  public <T> T createFromText(Class<T> aClass, final String text) {
    return createFromText(aClass, text, FROM_ROOT);
  }

  static int[] PATH_PARAMETER = {0, 3, 1};

  public PyNamedParameter createParameter(@NotNull String name) {
    return createFromText(PyNamedParameter.class, "def f(" + name + "): pass", PATH_PARAMETER);
  }

  // TODO: use to generate most other things
  public <T> T createFromText(Class<T> aClass, final String text, final int[] path) {
    final PsiFile dummyFile = createDummyFile(text);
    PsiElement ret = dummyFile;
    for (int skip : path) {
      ret = ret.getFirstChild();
      for (int i = 0; i < skip; i += 1) ret = ret.getNextSibling();
    }
    return (T)ret;
  }
}
