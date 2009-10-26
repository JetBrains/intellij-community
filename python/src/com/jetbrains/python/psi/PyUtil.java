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
import com.intellij.psi.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class PyUtil {
  private PyUtil() {
  }

  public static void ensureWritable(PsiElement element) {
    PsiDocumentManager docmgr = PsiDocumentManager.getInstance(element.getProject());
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return;
    Document doc = docmgr.getDocument(containingFile);
    if (doc == null) return;
    if (!FileDocumentManager.getInstance().requestWriting(doc, element.getProject())) {
      throw new IllegalStateException();
    }
  }

  @Nullable
  public static PsiElement addLineToExpression(PyExpression expr, PyExpression add, boolean forceAdd, Comparator<String> comparator) {
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
  public static
  @Nullable
  PsiElement addLineToExpressionIfMissing(
    PyExpression expr, PyExpression add, String line, boolean forceAdd, @Nullable Comparator<String> comparator
  ) {
    Project project = expr.getProject();
    if (expr instanceof PyListLiteralExpression) {
      PyListLiteralExpression listExp = (PyListLiteralExpression)expr;
      if (comparator != null && add instanceof PyStringLiteralExpression) {
        PyStringLiteralExpression addLiteral = (PyStringLiteralExpression)add;

        List<PyStringLiteralExpression> literals = new ArrayList<PyStringLiteralExpression>();
        for (PyExpression exp : listExp.getElements()) {
          if (exp instanceof PyStringLiteralExpression) {
            PyStringLiteralExpression str = (PyStringLiteralExpression)exp;
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
          }
          else if (quitnext) {
            break;
          }
        }
        try {
          if (after == null) {
            if (literals.size() == 0) {
              return listExp.add(add);
            }
            else {
              return listExp.addBefore(add, literals.get(0));
            }
          }
          else {
            return listExp.addAfter(add, after);
          }
        }
        catch (IncorrectOperationException e) {
          throw new RuntimeException(e);
        }

      }
      else {
        try {
          return listExp.add(add);
        }
        catch (IncorrectOperationException e) {
          throw new RuntimeException(e);
        }
      }

    }
    else if (expr instanceof PyBinaryExpression) {
      PyBinaryExpression binexp = (PyBinaryExpression)expr;
      if (binexp.isOperator("+")) {
        PsiElement b = addLineToExpressionIfMissing(binexp.getLeftExpression(), add, line, false, comparator);
        if (b != null) {
          return b;
        }
        PsiElement c = addLineToExpressionIfMissing(binexp.getRightExpression(), add, line, false, comparator);
        if (c != null) {
          return c;
        }
      }
    }
    if (forceAdd) {
      PyListLiteralExpression listLiteral = PythonLanguage.getInstance().getElementGenerator().createListLiteral(project);
      try {
        listLiteral.add(add);
      }
      catch (IncorrectOperationException e) {
        throw new IllegalStateException(e);
      }
      PyBinaryExpression binExpr =
        PythonLanguage.getInstance().getElementGenerator().createBinaryExpression(project, "+", expr, listLiteral);
      ASTNode exprNode = expr.getNode();
      assert exprNode != null;
      ASTNode parent = exprNode.getTreeParent();
      ASTNode binExprNode = binExpr.getNode();
      assert binExprNode != null;
      parent.replaceChild(exprNode, binExprNode);
      PyListLiteralExpression copiedListLiteral = (PyListLiteralExpression)binExpr.getRightExpression();
      assert copiedListLiteral != null;
      PyExpression[] expressions = copiedListLiteral.getElements();
      return expressions[expressions.length - 1];

    }
    else {
      return null;
    }
  }

  public static ASTNode getNextNonWhitespace(ASTNode after) {
    ASTNode node = after;
    do {
      node = node.getTreeNext();
    }
    while (isWhitespace(node));
    return node;
  }

  public static ASTNode getPrevNonWhitespace(ASTNode after) {
    ASTNode node = after;
    do {
      node = node.getTreePrev();
    }
    while (isWhitespace(node));
    return node;
  }

  private static boolean isWhitespace(ASTNode node) {
    return node != null && node.getElementType().equals(TokenType.WHITE_SPACE);
  }


  @Nullable
  public static PsiElement getFirstNonCommentAfter(PsiElement start) {
    PsiElement seeker = start;
    while (seeker instanceof PsiWhiteSpace || seeker instanceof PsiComment) seeker = seeker.getNextSibling();
    return seeker;
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
        else {
          receiver.add(exp);
        }
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
   *
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
   *
   * @param what      array to join
   * @param from      index of first element to include (may safely be bigger than array length)
   * @param upto      index of last element to include (may safely be bigger than array length)
   * @param separator string to put between elements
   * @param target    buffer to collect the result
   * @return target (for easy chaining)
   */
  @NotNull
  public static StringBuilder joinSubarray(
    @NotNull String[] what, int from, int upto, @NotNull String separator, @NotNull StringBuilder target
  ) {
    boolean made_step = false;
    for (int i = from; i <= upto; i += 1) {
      if (i >= what.length) break; // safety
      if (made_step) target.append(separator);
      else made_step = true;
      target.append(what[i]);
    }
    return target;
  }

  /**
   * Appends all elements of array to a string buffer, interspersing them with a separator: {@code ['a', 'b', 'c'] -> "a, b, c"}.
   * Actually calls {@link PyUtil#joinSubarray(String[], int, int, String, StringBuilder) joinSubarray} with right bounds.
   * @param what      array to join.
   * @param separator string to put between elements.
   * @param target    collects the result.
   * @return          target, for easy chaining.
   */
  @NotNull
  public static StringBuilder joinArray(
    @NotNull String[] what, @NotNull String separator, @NotNull StringBuilder target
  ) {
    return joinSubarray(what, 0, what.length, separator, target);
  }


  /**
   * Produce a reasonable representation of a PSI element, good for debugging.
   * @param elt element to represent; nulls and invalid nodes are ok.
   * @param cutAtEOL if true, representation stops at nearest EOL inside the element.
   * @return the representation.
   */
  @NotNull
  @NonNls
  public static String getReadableRepr(PsiElement elt, final boolean cutAtEOL) {
    if (elt == null) return "null!";
    ASTNode node = elt.getNode();
    if (node == null) {
      return "null";
    }
    else {
      String s = node.getText();
      int cut_pos;
      if (cutAtEOL) {
        cut_pos = s.indexOf('\n');
      }
      else {
        cut_pos = -1;
      }
      if (cut_pos < 0) cut_pos = s.length();
      return s.substring(0, Math.min(cut_pos, s.length()));
    }
  }

  /**
   * Does the same as {@code PsiTreeUtil.getParentOfType(element, PyElement.class);}
   * Please eschew.
   * @param element where to start.
   * @return first PyElement up the tree.
   */
  @Nullable
  @Deprecated
  public static PyElement getCoveringPyElement(PsiElement element) {
    while (element != null) {
      if (element instanceof PyElement) return (PyElement)element;
      element = element.getParent();
    }
    return null;
  }


  /**
   * Finds the first element to have given class, ascending to root.
   * Is the same as {@code PsiTreeUtil.getParentOfType(element, requiredElementType, false);} please eschew.
   * @param element where to start.
   * @param requiredElementType what class to look for.
   * @return the closest (grand)parent or the element itself.
   */
  @Nullable
  @Deprecated
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
   * @param element             which to process
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

  /**
   * Shows an information balloon in a reasonable place at the top right of the window.
   * @param project     our project
   * @param message     the text, HTML markup allowed
   * @param messageType message type, changes the icon and the background.
   */
  // TODO: move to a better place
  public static void showBalloon(Project project, String message, MessageType messageType) {
    // ripped from com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier
    final JFrame frame = WindowManager.getInstance().getFrame(project.isDefault() ? null : project);
    if (frame == null) return;
    final JComponent component = frame.getRootPane();
    if (component == null) return;
    final Rectangle rect = component.getVisibleRect();
    final Point p = new Point(rect.x + rect.width - 10, rect.y + 10);
    final RelativePoint point = new RelativePoint(component, p);

    JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, messageType.getDefaultIcon(), messageType.getPopupBackground(), null)
      .setShowCallout(false).setCloseButtonEnabled(true)
      .createBalloon().show(point, Balloon.Position.atLeft);
  }

  @NonNls
  /**
   * Returns a quoted string representation, or "null".
   */
  public static String nvl(Object s) {
    if (s != null) return "'" + s.toString() + "'";
    else return "null";
  }

  /**
   * Adds an item into a comma-separated list in a PSI tree. E.g. can turn "foo, bar" into "foo, bar, baz", adding commas as needed. 
   * @param parent     the element to represent the list; we're adding a child to it.
   * @param newItem    the element we're inserting (the "baz" in the example).
   * @param beforeThis node to mark the insertion point inside the list; must belong to a child of target. Set to null to add first element.
   * @param isFirst    true if we don't need a comma before the element we're adding.
   * @param isLast     true if we don't need a comma after the element we're adding.
   */
  public static void addListNode(PsiElement parent, PsiElement newItem, ASTNode beforeThis, boolean isFirst, boolean isLast) {
    ensureWritable(parent);
    ASTNode node = parent.getNode();
    assert node != null;
    ASTNode itemNode = newItem.getNode();
    assert itemNode != null;
    Project project = parent.getProject();
    PyElementGenerator gen = PythonLanguage.getInstance().getElementGenerator();
    if (!isFirst) node.addChild(gen.createComma(project), beforeThis);
    node.addChild(itemNode, beforeThis);
    if (!isLast) node.addChild(gen.createComma(project), beforeThis);
  }

  /**
   * Removes an element from a a comma-separated list in a PSI tree. E.g. can turn "foo, bar, baz" into "foo, baz",
   * removing commas as needed. It removes a trailing comma if it results from deletion.
   * @param item what to remove. Its parent is considered the list, and commas must be its peers.
   */
  public static void removeListNode(PsiElement item) {
    PsiElement parent = item.getParent();
    ensureWritable(parent);
    // remove comma after the item
    ASTNode binder = parent.getNode();
    assert binder != null : "parent node is null, ensureWritable() lied";
    boolean got_comma_after = eraseWhitespaceAndComma(binder, item, false);
    if (! got_comma_after) {
      // there was not a comma after the item; remove a comma before the item
      eraseWhitespaceAndComma(binder, item, true);
    }
    // finally
    item.delete();
  }

  /**
   * Removes whitespace and comma(s) that are siblings of the item, up to the first non-whitespace and non-comma.
   * @param parent_node node of the parent of item.
   * @param item        starting point; we erase left or right of it, but not it.
   * @param backwards   true to erase prev siblings, false to erase next siblings.
   * @return true       if a comma was found and removed.
   */
  private static boolean eraseWhitespaceAndComma(ASTNode parent_node, PsiElement item, boolean backwards) {
    // we operate on AST, PSI won't let us delete whitespace easily.
    boolean is_comma;
    boolean got_comma = false;
    ASTNode current = item.getNode();
    ASTNode candidate;
    boolean have_skipped_the_item = false;
    while (current != null) {
      candidate = current;
      current = backwards? current.getTreePrev() : current.getTreeNext();
      if (have_skipped_the_item) {
        is_comma = ",".equals(candidate.getText());
        got_comma |= is_comma;
        if (is_comma || candidate.getElementType() == TokenType.WHITE_SPACE) parent_node.removeChild(candidate);
        else break;
      }
      else have_skipped_the_item = true;
    }
    return got_comma;
  }

  /**
   * Collects superclasses of a class all the way up the inheritance chain. The order is <i>not</i> necessarily the MRO.
   */
  @NotNull
  public static PyClass[] getAllSuperClasses(@NotNull PyClass pyClass) {
    Set<PyClass> superClasses = new HashSet<PyClass>();
    List<PyClass> superClassesBuffer = new LinkedList<PyClass>();
    while (true) {
      final PyClass[] classes = pyClass.getSuperClasses();
      if (classes.length == 0) {
        break;
      }
      superClassesBuffer.addAll(Arrays.asList(classes));
      if (!superClasses.containsAll(Arrays.asList(classes))) {
        superClasses.addAll(Arrays.asList(classes));
      }
      else {
        break;
      }
      if (!superClassesBuffer.isEmpty()) {
        pyClass = superClassesBuffer.remove(0);
      }
      else {
        break;
      }
    }
    return superClasses.toArray(new PyClass[superClasses.size()]);
  }

  /**
   * Finds the first identifier AST node under target element, and returns its text.
   * @param target
   * @return identifier text, or null.
   */
  public static @Nullable
  String getIdentifier(PsiElement target) {
    ASTNode node = target.getNode();
    if (node != null) {
      ASTNode ident_node = node.findChildByType(PyTokenTypes.IDENTIFIER);
      if (ident_node != null) return ident_node.getText();
    }
    return null;
  }


  // TODO: move to a more proper place?
  /**
   * Determine the type of a special attribute. Currently supported: {@code __class__} and {@code __dict__}.
   * @param ref reference to a possible attribute; only qualified references make sense.
   * @return type, or null (if type cannot be determined, reference is not to a known attribute, etc.)
   */
  public static @Nullable
  PyType getSpecialAttributeType(PyReferenceExpression ref) {
    if (ref != null) {
      PyExpression qualifier = ref.getQualifier();
      if (qualifier != null) {
        String attr_name = getIdentifier(ref.getElement());
        if ("__class__".equals(attr_name)) {
          PyType qual_type = qualifier.getType();
          if (qual_type instanceof PyClassType) {
            return new PyClassType(((PyClassType)qual_type).getPyClass(), true); // always as class, never instance
          }
        }
        else if ("__dict__".equals(attr_name)) {
          PyType qual_type = qualifier.getType();
          if (qual_type instanceof PyClassType && ((PyClassType)qual_type).isDefinition()) {
            return PyBuiltinCache.getInstance(ref.getProject()).getDictType();
          }
        }
      }
    }
    return null;
  }

  /**
   * Makes sure that 'thing' is not null; else throws an {@link IncorrectOperationException}.
   * @param thing what we check.
   * @return thing, if not null.
   */
  @NotNull
  public static <T> T sure(T thing) {
    if (thing == null) throw new IncorrectOperationException();
    return thing;
  }

  /**
   * Makes sure that the 'thing' is true; else throws an {@link IncorrectOperationException}.
   * @param thing what we check.
   */
  public static void sure(boolean thing) {
    if (!thing) throw new IncorrectOperationException();
  }
}
