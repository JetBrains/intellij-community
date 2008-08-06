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

package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PyUtil {
    private PyUtil() {
    }

  public static void ensureWritable(PsiElement element) {
        PsiDocumentManager docmgr = PsiDocumentManager.getInstance(
                element.getProject());
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return;
        }
        Document doc = docmgr.getDocument(containingFile);
        if (doc == null) {
            return;
        }
        if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(
                doc, element.getProject())) {
            throw new IllegalStateException();
        }
    }

    public static PsiElement addLineToExpression(PyExpression expr,
                                                 PyExpression add,
                                                 boolean forceAdd,
                                                 Comparator<String> comparator) {
        return addLineToExpressionIfMissing(expr, add, null, forceAdd, comparator);
    }

    /**
     * Returns the element that was actually added, or {@code null} if no way
     * was found to add it and {@code forceAdd} was false. The returned
     * element's parent will always be a {@code PyListLiteralExpression}.
     * <br><br>
     * If the given {@code line} is already present, this method will not change
     * the PSI and it will return {@code null}.
     */
    public static @Nullable PsiElement addLineToExpressionIfMissing(
            PyExpression expr, PyExpression add, String line, boolean forceAdd,
            @Nullable Comparator<String> comparator) {
        Project project = expr.getProject();
        if (expr instanceof PyListLiteralExpression) {
            PyListLiteralExpression listExp = (PyListLiteralExpression) expr;
            if (comparator != null && add instanceof PyStringLiteralExpression)
            {
                PyStringLiteralExpression addLiteral = (PyStringLiteralExpression) add;

                List<PyStringLiteralExpression> literals
                        = new ArrayList<PyStringLiteralExpression>();
                for (PyExpression exp : listExp.getElements()) {
                    if (exp instanceof PyStringLiteralExpression) {
                        PyStringLiteralExpression str = (PyStringLiteralExpression) exp;
                        if (line != null && str.getStringValue().equals(line)) {
                            // the string is already present
                            return null;
                        }
                        literals.add(str);
                    }
                }

                String addval = addLiteral.getStringValue();
                PyStringLiteralExpression after = null;
                boolean quitnext = false;
                for (PyStringLiteralExpression literal : literals) {
                    String val = literal.getStringValue();
                    if (val == null) {
                        continue;
                    }

                    if (comparator.compare(val, addval) < 0) {
                        after = literal;
                        quitnext = true;
                    } else if (quitnext) {
                      break;
                    }
                }
                try {
                    if (after == null) {
                      if (literals.size() == 0) {
                          return listExp.add(add);
                      } else {
                          return listExp.addBefore(add, literals.get(0));
                      }
                    } else {
                        return listExp.addAfter(add, after);
                    }
                } catch (IncorrectOperationException e) {
                    throw new RuntimeException(e);
                }

            } else {
                try {
                    return listExp.add(add);
                } catch (IncorrectOperationException e) {
                    throw new RuntimeException(e);
                }
            }

        } else if (expr instanceof PyBinaryExpression) {
            PyBinaryExpression binexp = (PyBinaryExpression) expr;
            if (binexp.isOperator("+")) {
                PsiElement b = addLineToExpressionIfMissing(
                        binexp.getLeftExpression(), add, line, false,
                        comparator);
                if (b != null) {
                    return b;
                }
                PsiElement c = addLineToExpressionIfMissing(
                        binexp.getRightExpression(), add, line, false,
                        comparator);
                if (c != null) {
                    return c;
                }
            }
        }
        if (forceAdd) {
            PyListLiteralExpression listLiteral =
                    PythonLanguage.getInstance().getElementGenerator().createListLiteral(project);
            try {
                listLiteral.add(add);
            } catch (IncorrectOperationException e) {
                throw new IllegalStateException(e);
            }
            PyBinaryExpression binExpr = PythonLanguage.getInstance().getElementGenerator()
                    .createBinaryExpression(project, "+", expr, listLiteral);
            ASTNode exprNode = expr.getNode();
            assert exprNode != null;
            ASTNode parent = exprNode.getTreeParent();
            ASTNode binExprNode = binExpr.getNode();
            assert binExprNode != null;
            parent.replaceChild(exprNode, binExprNode);
            PyListLiteralExpression copiedListLiteral
                    = (PyListLiteralExpression) binExpr.getRightExpression();
            assert copiedListLiteral != null;
            PyExpression[] expressions = copiedListLiteral.getElements();
            return expressions[expressions.length - 1];

        } else {
            return null;
        }
    }

    public static ASTNode getNextNonWhitespace(ASTNode after) {
        ASTNode node = after;
        do {
            node = node.getTreeNext();
        } while (isWhitespace(node));
        return node;
    }

    public static ASTNode getPrevNonWhitespace(ASTNode after) {
        ASTNode node = after;
        do {
            node = node.getTreePrev();
        } while (isWhitespace(node));
        return node;
    }

  private static boolean isWhitespace(ASTNode node) {
    return node != null && node.getElementType().equals(TokenType.WHITE_SPACE);
  }

  /**
   * @see com.jetbrains.python.psi.PyUtil#flattenedParens(T[]) 
   */
  protected static <T extends PyElement> List<T> _unfoldParenExprs(T[] targets, List<T> receiver) {
    // NOTE: this proliferation of instanceofs is not very beautiful. Maybe rewrite using a visitor.
    for (T exp : targets) {
      if (exp instanceof PyParenthesizedExpression) {
        final PyParenthesizedExpression parex = (PyParenthesizedExpression)exp;
        PyExpression cont = parex.getContainedExpression();
        if (cont instanceof PyTupleExpression) {
          final PyTupleExpression tupex = (PyTupleExpression)cont;
          _unfoldParenExprs((T[])tupex.getElements(), receiver);
        }
        else receiver.add(exp);
      }
      else if (exp instanceof PyTupleExpression) {
        final PyTupleExpression tupex = (PyTupleExpression)exp;
        _unfoldParenExprs((T[])tupex.getElements(), receiver);
      }
      else {
        receiver.add(exp);
      }
    }
    return receiver;
  }

  // Poor man's catamorhpism :)
  /**
   * Flattens the representation of every element in targets, and puts all results together.
   * Elements of every tuple nested in target item are brought to the top level: (a, (b, (c, d))) -> (a, b, c, d)
   * Typical usage: <code>flattenedParens(some_tuple.getExpressions())</code>.
   * @param targets target elements.
   * @return the list of flattened expressions.
   */
  @NotNull
  public static <T extends PyElement> List<T> flattenedParens(T[] targets) {
    return _unfoldParenExprs(targets, new ArrayList<T>(targets.length));
  }

  // Poor man's filter
  // TODO: move to a saner place
  public static boolean instanceOf(Object obj, Class... possibleClasses) {
    for (Class cls : possibleClasses) {
      if (cls.isInstance(obj)) return true;
    }
    return false;
  }

}
