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

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Formatter;

/**
 * Created by IntelliJ IDEA. User: yole Date: 19.06.2005 Time: 13:45:32 To
 * change this template use File | Settings | File Templates.
 */
public class PyElementGeneratorImpl implements PyElementGenerator {
    private final PythonLanguage language;

    public PyElementGeneratorImpl(PythonLanguage language) {
        this.language = language;
    }

    public ASTNode createNameIdentifier(Project project, String name) {
        final PsiFile dummyFile = createDummyFile(project, name);
        final PyExpressionStatement expressionStatement =
                (PyExpressionStatement) dummyFile.getFirstChild();
        final PyReferenceExpression refExpression =
                (PyReferenceExpression) expressionStatement.getFirstChild();

        return refExpression.getNode().getFirstChildNode();
    }

    private PsiFile createDummyFile(Project project, String contents) {
        return language.createDummyFile(project, contents);
    }

    public PyStringLiteralExpression createStringLiteralAlreadyEscaped(
            Project project, String str) {
        final PsiFile dummyFile = createDummyFile(project, str);
        final PyExpressionStatement expressionStatement =
                (PyExpressionStatement) dummyFile.getFirstChild();
        return (PyStringLiteralExpression) expressionStatement.getFirstChild();
    }

    public PyStringLiteralExpression createStringLiteralFromString(
            Project project, @Nullable PsiFile destination, String unescaped) {
        boolean useDouble = !unescaped.contains("\"");
        boolean useMulti = unescaped.matches(".*(\r|\n).*");
        String quotes;
        if (useMulti) {
            quotes = useDouble ? "\"\"\"" : "'''";
        } else {
            quotes = useDouble ? "\"" : "'";
        }
        StringBuilder buf = new StringBuilder(unescaped.length() * 2);
        buf.append(quotes);
        VirtualFile vfile = destination == null ? null : destination.getVirtualFile();
        Charset charset;
        if (vfile == null) {
            charset = Charset.forName("US-ASCII");
        } else {
            charset = vfile.getCharset();
        }
        CharsetEncoder encoder = charset.newEncoder();
        Formatter formatter = new Formatter(buf);
        boolean unicode = false;
        for (int i = 0; i < unescaped.length(); i++) {
            int c = unescaped.codePointAt(i);
            if (c == '"' && useDouble) {
                buf.append("\\\"");
            } else if (c == '\'' && !useDouble) {
                buf.append("\\'");
            } else if ((c == '\r' || c == '\n') && !useMulti) {
                if (c == '\r') buf.append("\\r");
                else if (c == '\n') buf.append("\\n");
            } else if (!encoder.canEncode(new String(Character.toChars(c)))) {
                if (c <= 0xff) {
                    formatter.format("\\x%02x", c);
                } else if (c < 0xffff) {
                    unicode = true;
                    formatter.format("\\u%04x", c);
                } else {
                    unicode = true;
                    formatter.format("\\U%08x", c);
                }
            } else {
                buf.appendCodePoint(c);
            }
        }
        buf.append(quotes);
        if (unicode) buf.insert(0, "u");

        return createStringLiteralAlreadyEscaped(project, buf.toString());
    }

    public PyListLiteralExpression createListLiteral(Project project) {
        final PsiFile dummyFile = createDummyFile(project, "[]");
        final PyExpressionStatement expressionStatement =
                (PyExpressionStatement) dummyFile.getFirstChild();
        return (PyListLiteralExpression) expressionStatement.getFirstChild();
    }

    public PyKeywordArgument createKeywordArgument(Project project,
                                                   String keyword,
                                                   @Nullable PyExpression expression) {
        final PsiFile dummyFile = createDummyFile(project,
                "xyz(" + keyword + " = 0)");
        final PyExpressionStatement expressionStatement =
                (PyExpressionStatement) dummyFile.getFirstChild();
        PyCallExpression call =
                (PyCallExpression) expressionStatement.getFirstChild();
        PyKeywordArgument keywordArg =
                (PyKeywordArgument) call.getArgumentList().getArguments()[0];
        ASTNode valNode = keywordArg.getValueExpression().getNode();
        ASTNode valParent = valNode.getTreeParent();
        if (expression == null) {
            valParent.removeChild(valNode);
        } else {
            valParent.replaceChild(valNode, expression.getNode().copyElement());
        }
        return keywordArg;
    }

    public ASTNode createComma(Project project) {
        final PsiFile dummyFile = createDummyFile(project, "[0,]");
        final PyExpressionStatement expressionStatement =
                (PyExpressionStatement) dummyFile.getFirstChild();
        ASTNode zero = expressionStatement.getFirstChild().getNode()
                .getFirstChildNode().getTreeNext();
        return zero.getTreeNext().copyElement();
    }

    public PsiElement insertItemIntoList(Project project, PyElement list,
                                         @Nullable PyExpression afterThis,
                                         PyExpression toInsert)
            throws IncorrectOperationException {
        ASTNode add = toInsert.getNode().copyElement();
        if (afterThis == null) {
            ASTNode exprNode = list.getNode();
            ASTNode[] closingTokens = exprNode.getChildren(TokenSet.create(
                    PyTokenTypes.LBRACKET, PyTokenTypes.LPAR));
            if (closingTokens.length == 0) {
                // we tried our best. let's just insert it at the end
                exprNode.addChild(add);
            } else {
                ASTNode next = PyUtil.getNextNonWhitespace(closingTokens[closingTokens.length - 1]);
                if (next != null) {
                    ASTNode comma = createComma(project);
                    exprNode.addChild(comma, next);
                    exprNode.addChild(add, comma);
                } else {
                    exprNode.addChild(add);
                }
            }

        } else {
            ASTNode lastArgNode = afterThis.getNode();
            ASTNode comma = createComma(project);
            ASTNode parent = lastArgNode.getTreeParent();
            ASTNode afterLast = lastArgNode.getTreeNext();
            if (afterLast == null) {
                parent.addChild(add);
            } else {
                parent.addChild(add, afterLast);
            }
            parent.addChild(comma, add);
        }
        return add.getPsi();
    }

    public PyBinaryExpression createBinaryExpression(Project project,
                                                     String s,
                                                     PyExpression expr,
                                                     PyExpression listLiteral) {
        final PsiFile dummyFile = createDummyFile(project, "a " + s + " b");
        final PyExpressionStatement expressionStatement =
                (PyExpressionStatement) dummyFile.getFirstChild();
        PyBinaryExpression binExpr =
                (PyBinaryExpression) expressionStatement.getExpression();
        ASTNode binnode = binExpr.getNode();
        binnode.replaceChild(binExpr.getLeftExpression().getNode(),
                expr.getNode().copyElement());
        binnode.replaceChild(binExpr.getRightExpression().getNode(),
                listLiteral.getNode().copyElement());
        return binExpr;
    }

  public PyExpression createExpressionFromText(final Project project, final String text) {
    final PsiFile dummyFile = createDummyFile(project, text);
    final PyExpressionStatement expressionStatement =
                (PyExpressionStatement) dummyFile.getFirstChild();
    return expressionStatement.getExpression();
  }

  public PyCallExpression createCallExpression(Project project,
                                                 String functionName) {
        final PsiFile dummyFile = createDummyFile(project, functionName + "()");
        return (PyCallExpression) dummyFile.getFirstChild().getFirstChild();
    }

    public PyExpressionStatement createExpressionStatement(Project project,
                                                           PyExpression expr) {
        final PsiFile dummyFile = createDummyFile(project, "x");
        PyExpressionStatement stmt = (PyExpressionStatement) dummyFile.getFirstChild();
        stmt.getNode().replaceChild(stmt.getExpression().getNode(), expr.getNode());
        return stmt;
    }

    public void setStringValue(PyStringLiteralExpression string, String value) {
        ASTNode strNode = string.getNode();
        Project project = string.getProject();
        strNode.getTreeParent().replaceChild(strNode, createStringLiteralFromString(project, string.getContainingFile(), value).getNode());
    }

  public PyImportStatement createImportStatementFromText(final Project project, final String text) {
    final PsiFile dummyFile = createDummyFile(project, text);
    return (PyImportStatement)dummyFile.getFirstChild();
  }
}
