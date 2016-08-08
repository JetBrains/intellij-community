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

import com.google.common.base.Preconditions;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author max
 */
public class PyPsiUtils {

  private static final Logger LOG = Logger.getInstance(PyPsiUtils.class.getName());

  private PyPsiUtils() {
  }

  @NotNull
  protected static <T extends PyElement> T[] nodesToPsi(ASTNode[] nodes, T[] array) {
    T[] psiElements = (T[])Array.newInstance(array.getClass().getComponentType(), nodes.length);
    for (int i = 0; i < nodes.length; i++) {
      //noinspection unchecked
      psiElements[i] = (T)nodes[i].getPsi();
    }
    return psiElements;
  }

  /**
   * Finds the closest comma after the element skipping any whitespaces in-between.
   */
  @Nullable
  public static PsiElement getPrevComma(@NotNull PsiElement element) {
    final PsiElement prevNode = getPrevNonWhitespaceSibling(element);
    return prevNode != null && prevNode.getNode().getElementType() == PyTokenTypes.COMMA ? prevNode : null;
  }

  /**
   * Finds first non-whitespace sibling before given PSI element.
   */
  @Nullable
  public static PsiElement getPrevNonWhitespaceSibling(@Nullable PsiElement element) {
    return PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
  }

  /**
   * Finds first non-whitespace sibling before given AST node.
   */
  @Nullable
  public static ASTNode getPrevNonWhitespaceSibling(@NotNull ASTNode node) {
    return skipSiblingsBackward(node, TokenSet.create(TokenType.WHITE_SPACE));
  }

  /**
   * Finds first sibling that is neither comment, nor whitespace before given element.
   * @param strict prohibit returning element itself
   */
  @Nullable
  public static PsiElement getPrevNonCommentSibling(@Nullable PsiElement start, boolean strict) {
    if (!strict && !(start instanceof PsiWhiteSpace || start instanceof PsiComment)) {
      return start;
    }
    return PsiTreeUtil.skipSiblingsBackward(start, PsiWhiteSpace.class, PsiComment.class);
  }

  /**
   * Finds the closest comma after the element skipping any whitespaces in-between.
   */
  @Nullable
  public static PsiElement getNextComma(@NotNull PsiElement element) {
    final PsiElement nextNode = getNextNonWhitespaceSibling(element);
    return nextNode != null && nextNode.getNode().getElementType() == PyTokenTypes.COMMA ? nextNode : null;
  }

  /**
   * Finds first non-whitespace sibling after given PSI element.
   */
  @Nullable
  public static PsiElement getNextNonWhitespaceSibling(@Nullable PsiElement element) {
    return PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
  }

  /**
   * Finds first non-whitespace sibling after given PSI element but stops at first whitespace containing line feed.  
   */
  @Nullable
  public static PsiElement getNextNonWhitespaceSiblingOnSameLine(@NotNull PsiElement element) {
    PsiElement cur = element.getNextSibling();
    while (cur != null) {
      if (!(cur instanceof PsiWhiteSpace)) {
        return cur;
      }
      else if (cur.textContains('\n')) {
        break;
      }
      cur = cur.getNextSibling();
    }
    return null;
  }
  
  /**
   * Finds first non-whitespace sibling after given AST node.
   */
  @Nullable
  public static ASTNode getNextNonWhitespaceSibling(@NotNull ASTNode after) {
    return skipSiblingsForward(after, TokenSet.create(TokenType.WHITE_SPACE));
  }

  /**
   * Finds first sibling that is neither comment, nor whitespace after given element.
   * @param strict prohibit returning element itself
   */
  @Nullable
  public static PsiElement getNextNonCommentSibling(@Nullable PsiElement start, boolean strict) {
    if (!strict && !(start instanceof PsiWhiteSpace || start instanceof PsiComment)) {
      return start;
    }
    return PsiTreeUtil.skipSiblingsForward(start, PsiWhiteSpace.class, PsiComment.class);
  }

  /**
   * Finds first token after given element that doesn't consist solely of spaces and is not empty (e.g. error marker).
   * @param ignoreComments ignore commentaries as well
   */
  @Nullable
  public static PsiElement getNextSignificantLeaf(@Nullable PsiElement element, boolean ignoreComments) {
    while (element != null && StringUtil.isEmptyOrSpaces(element.getText()) || ignoreComments && element instanceof PsiComment) {
      element = PsiTreeUtil.nextLeaf(element);
    }
    return element;
  }

  /**
   * Finds first token before given element that doesn't consist solely of spaces and is not empty (e.g. error marker).
   * @param ignoreComments ignore commentaries as well
   */
  @Nullable
  public static PsiElement getPrevSignificantLeaf(@Nullable PsiElement element, boolean ignoreComments) {
    while (element != null && StringUtil.isEmptyOrSpaces(element.getText()) || ignoreComments && element instanceof PsiComment) {
      element = PsiTreeUtil.prevLeaf(element);
    }
    return element;
  }

  /**
   * Finds the closest comma looking for the next comma first and then for the preceding one.
   */
  @Nullable
  public static PsiElement getAdjacentComma(@NotNull PsiElement element) {
    final PsiElement nextComma = getNextComma(element);
    return nextComma != null ? nextComma : getPrevComma(element);
  }

  /**
   * Works similarly to {@link PsiTreeUtil#skipSiblingsForward(PsiElement, Class[])}, but for AST nodes.
   */
  @Nullable
  public static ASTNode skipSiblingsForward(@Nullable ASTNode node, @NotNull TokenSet types) {
    if (node == null) {
      return null;
    }
    for (ASTNode next = node.getTreeNext(); next != null; next = next.getTreeNext()) {
      if (!types.contains(next.getElementType())) {
        return next;
      }
    }
    return null;
  }

  /**
   * Works similarly to {@link PsiTreeUtil#skipSiblingsBackward(PsiElement, Class[])}, but for AST nodes.
   */
  @Nullable
  public static ASTNode skipSiblingsBackward(@Nullable ASTNode node, @NotNull TokenSet types) {
    if (node == null) {
      return null;
    }
    for (ASTNode prev = node.getTreePrev(); prev != null; prev = prev.getTreePrev()) {
      if (!types.contains(prev.getElementType())) {
        return prev;
      }
    }
    return null;
  }

    /**
   * Returns first child psi element with specified element type or {@code null} if no such element exists.
   * Semantically it's the same as {@code getChildByFilter(element, TokenSet.create(type), 0)}.
   *
   * @param element tree parent node
   * @param type    element type expected
   * @return child element described
   */
  @Nullable
  public static PsiElement getFirstChildOfType(@NotNull final PsiElement element, @NotNull PyElementType type) {
    final ASTNode child = element.getNode().findChildByType(type);
    return child != null ? child.getPsi() : null;
  }

  /**
   * Returns child element in the psi tree
   *
   * @param filter  Types of expected child
   * @param number  number
   * @param element tree parent node
   * @return PsiElement - child psiElement
   */
  @Nullable
  public static PsiElement getChildByFilter(@NotNull PsiElement element, @NotNull TokenSet filter, int number) {
    final ASTNode node = element.getNode();
    if (node != null) {
      final ASTNode[] children = node.getChildren(filter);
      return (0 <= number && number < children.length) ? children[number].getPsi() : null;
    }
    return null;
  }

  public static void addBeforeInParent(@NotNull final PsiElement anchor, @NotNull final PsiElement... newElements) {
    final ASTNode anchorNode = anchor.getNode();
    LOG.assertTrue(anchorNode != null);
    for (PsiElement newElement : newElements) {
      anchorNode.getTreeParent().addChild(newElement.getNode(), anchorNode);
    }
  }

  public static void removeElements(@NotNull final PsiElement... elements) {
    final ASTNode parentNode = elements[0].getParent().getNode();
    LOG.assertTrue(parentNode != null);
    for (PsiElement element : elements) {
      //noinspection ConstantConditions
      parentNode.removeChild(element.getNode());
    }
  }

  @Nullable
  public static PsiElement getStatement(@NotNull final PsiElement element) {
    final PyElement compStatement = getStatementList(element);
    if (compStatement == null) {
      return null;
    }
    return getParentRightBefore(element, compStatement);
  }

  public static PyElement getStatementList(final PsiElement element) {
    //noinspection ConstantConditions
    return element instanceof PyFile || element instanceof PyStatementList
           ? (PyElement)element
           : PsiTreeUtil.getParentOfType(element, PyFile.class, PyStatementList.class);
  }

  /**
   * Returns ancestor of the element that is also direct child of the given super parent.
   *
   * @param element     element to start search from
   * @param superParent direct parent of the desired ancestor
   * @return described element or {@code null} if it doesn't exist
   */
  @Nullable
  public static PsiElement getParentRightBefore(@NotNull PsiElement element, @NotNull final PsiElement superParent) {
    return PsiTreeUtil.findFirstParent(element, false, element1 -> element1.getParent() == superParent);
  }

  public static List<PsiElement> collectElements(final PsiElement statement1, final PsiElement statement2) {
    // Process ASTNodes here to handle all the nodes
    final ASTNode node1 = statement1.getNode();
    final ASTNode node2 = statement2.getNode();
    final ASTNode parentNode = node1.getTreeParent();

    boolean insideRange = false;
    final List<PsiElement> result = new ArrayList<>();
    for (ASTNode node : parentNode.getChildren(null)) {
      // start
      if (node1 == node) {
        insideRange = true;
      }
      if (insideRange) {
        result.add(node.getPsi());
      }
      // stop
      if (node == node2) {
        break;
      }
    }
    return result;
  }

  public static int getElementIndentation(final PsiElement element) {
    final PsiElement compStatement = getStatementList(element);
    final PsiElement statement = getParentRightBefore(element, compStatement);
    if (statement == null) {
      return 0;
    }
    PsiElement sibling = statement.getPrevSibling();
    if (sibling == null) {
      sibling = compStatement.getPrevSibling();
    }
    final String whitespace = sibling instanceof PsiWhiteSpace ? sibling.getText() : "";
    final int i = whitespace.lastIndexOf("\n");
    return i != -1 ? whitespace.length() - i - 1 : 0;
  }

  public static void removeRedundantPass(final PyStatementList statementList) {
    final PyStatement[] statements = statementList.getStatements();
    if (statements.length > 1) {
      for (PyStatement statement : statements) {
        if (statement instanceof PyPassStatement) {
          statement.delete();
        }
      }
    }
  }

  public static boolean isMethodContext(final PsiElement element) {
    final PsiNamedElement parent = PsiTreeUtil.getParentOfType(element, PyFile.class, PyFunction.class, PyClass.class);
    // In case if element is inside method which is inside class
    if (parent instanceof PyFunction && PsiTreeUtil.getParentOfType(parent, PyFile.class, PyClass.class) instanceof PyClass) {
      return true;
    }
    return false;
  }


  @NotNull
  public static PsiElement getRealContext(@NotNull final PsiElement element) {
    assertValid(element);
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyExpressionCodeFragment) {
      final PsiElement context = file.getContext();
      if (LOG.isDebugEnabled()) {
        LOG.debug("PyPsiUtil.getRealContext(" + element + ") is called. Returned " + context + ". Element inside code fragment");
      }
      return context != null ? context : element;
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("PyPsiUtil.getRealContext(" + element + ") is called. Returned " + element + ".");
      }
      return element;
    }
  }

  /**
   * Removes comma closest to the given child node along with any whitespaces around. First following comma is checked and only
   * then, if it doesn't exists, preceding one.
   *
   * @param element parent node
   * @param child   child node comma should be adjacent to
   * @see #getAdjacentComma(PsiElement)
   */
  public static void deleteAdjacentCommaWithWhitespaces(@NotNull PsiElement element, @NotNull PsiElement child) {
    final PsiElement commaNode = getAdjacentComma(child);
    if (commaNode != null) {
      final PsiElement nextNonWhitespace = getNextNonWhitespaceSibling(commaNode);
      final PsiElement last = nextNonWhitespace == null ? element.getLastChild() : nextNonWhitespace.getPrevSibling();
      final PsiElement prevNonWhitespace = getPrevNonWhitespaceSibling(commaNode);
      final PsiElement first = prevNonWhitespace == null ? element.getFirstChild() : prevNonWhitespace.getNextSibling();
      element.deleteChildRange(first, last);
    }
  }

  /**
   * Returns comments preceding given elements as pair of the first and the last such comments. Comments should not be
   * separated by any empty line.
   * @param element element comments should be adjacent to
   * @return described range or {@code null} if there are no such comments
   */
  @Nullable
  public static Couple<PsiComment> getPrecedingComments(@NotNull PsiElement element) {
    PsiComment firstComment = null, lastComment = null;
    overComments:
    while (true) {
      int newLinesCount = 0;
      for (element = element.getPrevSibling(); element instanceof PsiWhiteSpace; element = element.getPrevSibling()) {
        newLinesCount += StringUtil.getLineBreakCount(element.getText());
        if (newLinesCount > 1) {
          break overComments;
        }
      }
      if (element instanceof PsiComment) {
        if (lastComment == null) {
          lastComment = (PsiComment)element;
        }
        firstComment = (PsiComment)element;
      }
      else {
        break;
      }
    }
    return lastComment == null ? null : Couple.of(firstComment, lastComment);
  }

  @NotNull
  static <T, U extends PsiElement> List<T> collectStubChildren(U e,
                                                               final StubElement<U> stub, final IElementType elementType,
                                                               final Class<T> itemClass) {
    final List<T> result = new ArrayList<>();
    if (stub != null) {
      final List<StubElement> children = stub.getChildrenStubs();
      for (StubElement child : children) {
        if (child.getStubType() == elementType) {
          //noinspection unchecked
          result.add((T)child.getPsi());
        }
      }
    }
    else {
      e.acceptChildren(new TopLevelVisitor() {
        @Override
        protected void checkAddElement(PsiElement node) {
          if (node.getNode().getElementType() == elementType) {
            //noinspection unchecked
            result.add((T)node);
          }
        }
      });
    }
    return result;
  }

  static List<PsiElement> collectAllStubChildren(PsiElement e, StubElement stub) {
    final List<PsiElement> result = new ArrayList<>();
    if (stub != null) {
      final List<StubElement> children = stub.getChildrenStubs();
      for (StubElement child : children) {
        //noinspection unchecked
        result.add(child.getPsi());
      }
    }
    else {
      e.acceptChildren(new TopLevelVisitor() {
        @Override
        protected void checkAddElement(PsiElement node) {
          result.add(node);
        }
      });
    }
    return result;
  }

  public static int findArgumentIndex(PyCallExpression call, PsiElement argument) {
    final PyExpression[] args = call.getArguments();
    for (int i = 0; i < args.length; i++) {
      PyExpression expression = args[i];
      if (expression instanceof PyKeywordArgument) {
        expression = ((PyKeywordArgument)expression).getValueExpression();
      }
      expression = flattenParens(expression);
      if (expression == argument) {
        return i;
      }
    }
    return -1;
  }

  @Nullable
  public static PyTargetExpression getAttribute(@NotNull final PyFile file, @NotNull final String name) {
    PyTargetExpression attr = file.findTopLevelAttribute(name);
    if (attr == null) {
      for (PyFromImportStatement element : file.getFromImports()) {
        PyReferenceExpression expression = element.getImportSource();
        if (expression == null) continue;
        final PsiElement resolved = expression.getReference().resolve();
        if (resolved instanceof PyFile && resolved != file) {
          return ((PyFile)resolved).findTopLevelAttribute(name);
        }
      }
    }
    return attr;
  }

  public static List<PyExpression> getAttributeValuesFromFile(@NotNull PyFile file, @NotNull String name) {
    List<PyExpression> result = ContainerUtil.newArrayList();
    final PyTargetExpression attr = file.findTopLevelAttribute(name);
    if (attr != null) {
      sequenceToList(result, attr.findAssignedValue());
    }
    return result;
  }

  public static void sequenceToList(List<PyExpression> result, PyExpression value) {
    value = flattenParens(value);
    if (value instanceof PySequenceExpression) {
      result.addAll(ContainerUtil.newArrayList(((PySequenceExpression)value).getElements()));
    }
    else {
      result.add(value);
    }
  }

  public static List<String> getStringValues(PyExpression[] elements) {
    List<String> results = ContainerUtil.newArrayList();
    for (PyExpression element : elements) {
      if (element instanceof PyStringLiteralExpression) {
        results.add(((PyStringLiteralExpression)element).getStringValue());
      }
    }
    return results;
  }

  @Nullable
  public static PyExpression flattenParens(@Nullable PyExpression expr) {
    while (expr instanceof PyParenthesizedExpression) {
      expr = ((PyParenthesizedExpression)expr).getContainedExpression();
    }
    return expr;
  }

  @Nullable
  public static String strValue(@Nullable PyExpression expression) {
    return expression instanceof PyStringLiteralExpression ? ((PyStringLiteralExpression)expression).getStringValue() : null;
  }

  public static boolean isBefore(@NotNull final PsiElement element, @NotNull final PsiElement element2) {
    // TODO: From RubyPsiUtil, should be moved to PsiTreeUtil
    return element.getTextOffset() <= element2.getTextOffset();
  }

  @Nullable
  public static QualifiedName asQualifiedName(@Nullable PyExpression expr) {
    return expr instanceof PyQualifiedExpression ? ((PyQualifiedExpression)expr).asQualifiedName() : null;
  }

  @Nullable
  public static PyExpression getFirstQualifier(@NotNull PyQualifiedExpression expr) {
    final List<PyExpression> expressions = unwindQualifiers(expr);
    if (!expressions.isEmpty()) {
      return expressions.get(0);
    }
    return null;
  }

  @NotNull
  public static String toPath(@Nullable PyQualifiedExpression expr) {
    if (expr != null) {
      final QualifiedName qName = expr.asQualifiedName();
      if (qName != null) {
        return qName.toString();
      }
      final String name = expr.getName();
      if (name != null) {
        return name;
      }
    }
    return "";
  }

  @Nullable
  protected static QualifiedName asQualifiedName(@NotNull PyQualifiedExpression expr) {
    return fromReferenceChain(unwindQualifiers(expr));
  }

  @NotNull
  private static List<PyExpression> unwindQualifiers(@NotNull final PyQualifiedExpression expr) {
    final List<PyExpression> path = new LinkedList<>();
    PyQualifiedExpression e = expr;
    while (e != null) {
      path.add(0, e);
      final PyExpression q = e.getQualifier();
      e = q instanceof PyQualifiedExpression ? (PyQualifiedExpression)q : null;
    }
    return path;
  }

  @Nullable
  private static QualifiedName fromReferenceChain(@NotNull List<PyExpression> components) {
    final List<String> componentNames = new ArrayList<>(components.size());
    for (PyExpression component : components) {
      final String refName = (component instanceof PyQualifiedExpression) ? ((PyQualifiedExpression)component).getReferencedName() : null;
      if (refName == null) {
        return null;
      }
      componentNames.add(refName);
    }
    return QualifiedName.fromComponents(componentNames);
  }

  /**
   * Wrapper for {@link PsiUtilCore#ensureValid(PsiElement)} that skips nulls
   */
  public static void assertValid(@Nullable final PsiElement element) {
    if (element == null) {
      return;
    }
    PsiUtilCore.ensureValid(element);
  }

  public static void assertValid(@NotNull final Module module) {
    Preconditions.checkArgument(!module.isDisposed(), String.format("Module %s is disposed", module));
  }

  @NotNull
  public static PsiFileSystemItem getFileSystemItem(@NotNull PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return (PsiFileSystemItem)element;
    }
    return element.getContainingFile();
  }

  private static abstract class TopLevelVisitor extends PyRecursiveElementVisitor {
    public void visitPyElement(final PyElement node) {
      super.visitPyElement(node);
      checkAddElement(node);
    }

    public void visitPyClass(final PyClass node) {
      checkAddElement(node);  // do not recurse into functions
    }

    public void visitPyFunction(final PyFunction node) {
      checkAddElement(node);  // do not recurse into classes
    }

    protected abstract void checkAddElement(PsiElement node);
  }
}