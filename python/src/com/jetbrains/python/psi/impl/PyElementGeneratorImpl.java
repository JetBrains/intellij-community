package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
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
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), name);
    final PyExpressionStatement expressionStatement = (PyExpressionStatement)dummyFile.getFirstChild();
    final PyReferenceExpression refExpression = (PyReferenceExpression)expressionStatement.getFirstChild();

    return refExpression.getNode().getFirstChildNode();
  }

  @Override
  public PsiFile createDummyFile(LanguageLevel langLevel, String contents) {
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    final String name = "dummy." + PythonFileType.INSTANCE.getDefaultExtension();
    final LightVirtualFile virtualFile = new LightVirtualFile(name, PythonFileType.INSTANCE, contents);
    virtualFile.putUserData(LanguageLevel.KEY, langLevel);
    final PsiFile psiFile = ((PsiFileFactoryImpl)factory).trySetupPsiForFile(virtualFile, PythonLanguage.getInstance(), false, true);
    assert psiFile != null;
    return psiFile;
  }

  public PyStringLiteralExpression createStringLiteralAlreadyEscaped(String str) {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "a=" + str);
    final PyAssignmentStatement expressionStatement = (PyAssignmentStatement)dummyFile.getFirstChild();
    return (PyStringLiteralExpression)expressionStatement.getAssignedValue();
  }


  public PyStringLiteralExpression createStringLiteralFromString(@NotNull String unescaped) {
    return createStringLiteralFromString(null, unescaped);
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

  public PyCallExpression createCallExpression(final LanguageLevel langLevel, String functionName) {
    final PsiFile dummyFile = createDummyFile(langLevel, functionName + "()");
    final PsiElement child = dummyFile.getFirstChild();
    if (child != null) {
      final PsiElement element = child.getFirstChild();
      if (!(element instanceof PyCallExpression)) {
        throw new IllegalArgumentException("Invalid call expression text " + functionName);
      }
      return (PyCallExpression)element;
    }
    return null;
  }

  public PyImportStatement createImportStatementFromText(final String text) {
    final PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), text);
    return (PyImportStatement)dummyFile.getFirstChild();
  }

  @Override
  public PyImportElement createImportElement(String name) {
    return createFromText(LanguageLevel.getDefault(), PyImportElement.class, "from foo import " + name, new int[]{0, 6});
  }

  static final int[] FROM_ROOT = new int[]{0};

  public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text) {
    return createFromText(langLevel, aClass, text, FROM_ROOT);
  }

  static int[] PATH_PARAMETER = {0, 3, 1};

  public PyNamedParameter createParameter(@NotNull String name) {
    return createFromText(LanguageLevel.getDefault(), PyNamedParameter.class, "def f(" + name + "): pass", PATH_PARAMETER);
  }

  // TODO: use to generate most other things
  public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text, final int[] path) {
    PsiElement ret = createDummyFile(langLevel, text);
    for (int skip : path) {
      if (ret != null) {
        ret = ret.getFirstChild();
        for (int i = 0; i < skip; i += 1) {
          if (ret != null) {
            ret = ret.getNextSibling();
          }
          else {
            return null;
          }
        }
      }
      else {
        return null;
      }
    }
    try {
      //noinspection unchecked
      return (T)ret;
    }
    catch (ClassCastException e) {
      return null;
    }
  }

  public PyExpressionStatement createDocstring(String content) {
    return createFromText(LanguageLevel.getDefault(),
                          PyExpressionStatement.class, content + "\n");
  }
}
