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
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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
   * @see PyUtil#flattenedParens 
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

  /**
   * Appends elements of array to a string buffer, interspersing them with a separator: {@code ['a', 'b', 'c'] -> "a, b, c"}.
   * @param what array to join
   * @param from index of first element to include (may safely be bigger than array length)
   * @param upto index of last element to include (may safely be bigger than array length)
   * @param separator string to put between elements
   * @param target buffer to collect the result
   * @return target (for easy chaining)
   */
  @NotNull
  public static StringBuffer joinSubarray(@NotNull String[] what, int from, int upto, @NotNull String separator, @NotNull StringBuffer target) {
    boolean made_step = false;
    for (int i = from; i <= upto; i+=1) {
      if (i >= what.length) break; // safety
      if (made_step) {
        target.append(separator);
      }
      else made_step = true;
      target.append(what[i]);
    }
    return target;
  }

  @NotNull @NonNls
  public static String getReadableRepr(PsiElement elt, final boolean cutAtEOL) {
    if (elt == null) return "null!";
    ASTNode node = elt.getNode();
    if (node == null) return "null";
    else {
      String s = node.getText();
      int cut_pos;
      if (cutAtEOL) {
        cut_pos = s.indexOf('\n');
      }
      else {
        cut_pos = -1;
      }
      if (cut_pos < 0)  cut_pos = s.length();
      return s.substring(0, Math.min(cut_pos, s.length()));
    }
  }

  @Nullable
  public static PyElement getCoveringPyElement(PsiElement element) {
    while (element != null) {
      if (element instanceof PyElement) return (PyElement)element;

      element = element.getParent();
    }

    return null;
  }

  @Nullable
  public static <T extends PyElement> T getElementOrParent(final PyElement element, final Class<T> requiredElementType) {
    if (element == null) return null;
    if (requiredElementType.isInstance(element)) {
      //noinspection unchecked
      return (T)element;
    }

    final PsiElement parent = element.getParent();
    if (parent != null && requiredElementType.isInstance(parent)) {
      //noinspection unchecked
      return (T)parent;
    }

    return null;
  }

  /**
   * @param element which to process
   * @param requiredElementType which type of container element is required
   * @return closest containing element of given type, or element itself, it it is of required type.
   */
  @Nullable
  public static <T extends PyElement> T getElementOrContaining(final PyElement element, final Class<T> requiredElementType) {
    if (element == null) return null;
    if (requiredElementType.isInstance(element)) {
      //noinspection unchecked
      return (T)element;
    }

    final PsiElement parent = element.getContainingElement(requiredElementType);
    if (parent != null) {
      //noinspection unchecked
      return (T)parent;
    }

    return null;
  }

  /**
   * @param element for which to obtain the file
   * @return PyFile, or null, if there's no containing file, or it is not a PyFile.
   */
  @Nullable
  public static PyFile getContainingPyFile(PyElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile instanceof PyFile ? (PyFile)containingFile : null;
  }

  // TODO: move to a better place
  public static void showBalloon(Project project, String message, MessageType messageType) {
    // ripped from com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier
    final JFrame frame = WindowManager.getInstance().getFrame(project.isDefault() ? null : project);
    if (frame == null) return;
    final JComponent component = frame.getRootPane();
    if (component == null) return;
    final Rectangle rect = component.getVisibleRect();
    final Point p = new Point(rect.x + 30, rect.y + rect.height - 10);
    final RelativePoint point = new RelativePoint(component, p);

    JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
      message, messageType.getDefaultIcon(), messageType.getPopupBackground(), null).createBalloon().show(
        point, Balloon.Position.above
    );
  }

  @NonNls
  /**
   * Returns a quoted string representation, or "null".
   */
  public static String nvl(Object s) {
    if (s != null) {
      return "'" + s.toString() + "'";
    }
    else {
      return "null";
    }
  }

  public static void addListNode(PsiElement target, PsiElement source, ASTNode beforeThis, boolean isFirst, boolean isLast) {
    ensureWritable(target);
    ASTNode node = target.getNode();
    assert node != null;
    ASTNode itemNode = source.getNode();
    assert itemNode != null;
    Project project = target.getProject();
    PyElementGenerator gen = PythonLanguage.getInstance().getElementGenerator();
    if (! isFirst) node.addChild(gen.createComma(project), beforeThis);
    node.addChild(itemNode, beforeThis);
    if (! isLast) node.addChild(gen.createComma(project), beforeThis);
  }

}
