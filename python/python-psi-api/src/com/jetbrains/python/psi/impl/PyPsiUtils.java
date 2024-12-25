// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class PyPsiUtils {

  private static final Logger LOG = Logger.getInstance(PyPsiUtils.class.getName());

  private PyPsiUtils() {
  }

  /**
   * Finds the closest comma after the element skipping any whitespaces in-between.
   */
  public static @Nullable PsiElement getPrevComma(@NotNull PsiElement element) {
    final PsiElement prevNode = getPrevNonWhitespaceSibling(element);
    return prevNode != null && prevNode.getNode().getElementType() == PyTokenTypes.COMMA ? prevNode : null;
  }

  /**
   * Finds first non-whitespace sibling before given PSI element.
   */
  public static @Nullable PsiElement getPrevNonWhitespaceSibling(@Nullable PsiElement element) {
    return PsiTreeUtil.skipWhitespacesBackward(element);
  }

  /**
   * Finds first non-whitespace sibling before given AST node.
   */
  public static @Nullable ASTNode getPrevNonWhitespaceSibling(@NotNull ASTNode node) {
    return skipSiblingsBackward(node, TokenSet.WHITE_SPACE);
  }

  /**
   * Finds first sibling that is neither comment, nor whitespace before given element.
   * @param strict prohibit returning element itself
   */
  public static @Nullable PsiElement getPrevNonCommentSibling(@Nullable PsiElement start, boolean strict) {
    if (!strict && !(start instanceof PsiWhiteSpace || start instanceof PsiComment)) {
      return start;
    }
    return PsiTreeUtil.skipWhitespacesAndCommentsBackward(start);
  }

  /**
   * Finds the closest comma after the element skipping any whitespaces in-between.
   */
  public static @Nullable PsiElement getNextComma(@NotNull PsiElement element) {
    final PsiElement nextNode = getNextNonWhitespaceSibling(element);
    return nextNode != null && nextNode.getNode().getElementType() == PyTokenTypes.COMMA ? nextNode : null;
  }

  /**
   * Finds first non-whitespace sibling after given PSI element.
   */
  public static @Nullable PsiElement getNextNonWhitespaceSibling(@Nullable PsiElement element) {
    return PsiTreeUtil.skipWhitespacesForward(element);
  }

  /**
   * Returns the first non-whitespace sibling preceding the given element but within its line boundaries.
   */
  public static @Nullable PsiElement getPrevNonWhitespaceSiblingOnSameLine(@NotNull PsiElement element) {
    PsiElement cur = element.getPrevSibling();
    while (cur != null) {
      if (!(cur instanceof PsiWhiteSpace)) {
        return cur;
      }
      else if (cur.textContains('\n')) {
        break;
      }
      cur = cur.getPrevSibling();
    }
    return null;
  }

  /**
   * Finds first non-whitespace sibling after given AST node.
   */
  public static @Nullable ASTNode getNextNonWhitespaceSibling(@NotNull ASTNode after) {
    return skipSiblingsForward(after, TokenSet.WHITE_SPACE);
  }

  /**
   * Finds first sibling that is neither comment, nor whitespace after given element.
   * @param strict prohibit returning element itself
   */
  public static @Nullable PsiElement getNextNonCommentSibling(@Nullable PsiElement start, boolean strict) {
    return PyPsiUtilsCore.getNextNonCommentSibling(start, strict);
  }

  /**
   * Finds first token after given element that doesn't consist solely of spaces and is not empty (e.g. error marker).
   * @param ignoreComments ignore commentaries as well
   */
  public static @Nullable PsiElement getNextSignificantLeaf(@Nullable PsiElement element, boolean ignoreComments) {
    while (element != null && StringUtil.isEmptyOrSpaces(element.getText()) || ignoreComments && element instanceof PsiComment) {
      element = PsiTreeUtil.nextLeaf(element);
    }
    return element;
  }

  /**
   * Finds first token before given element that doesn't consist solely of spaces and is not empty (e.g. error marker).
   * @param ignoreComments ignore commentaries as well
   */
  public static @Nullable PsiElement getPrevSignificantLeaf(@Nullable PsiElement element, boolean ignoreComments) {
    while (element != null && StringUtil.isEmptyOrSpaces(element.getText()) || ignoreComments && element instanceof PsiComment) {
      element = PsiTreeUtil.prevLeaf(element);
    }
    return element;
  }

  /**
   * Finds the closest comma looking for the next comma first and then for the preceding one.
   */
  public static @Nullable PsiElement getAdjacentComma(@NotNull PsiElement element) {
    final PsiElement nextComma = getNextComma(element);
    return nextComma != null ? nextComma : getPrevComma(element);
  }

  /**
   * Works similarly to {@link PsiTreeUtil#skipSiblingsForward(PsiElement, Class[])}, but for AST nodes.
   */
  public static @Nullable ASTNode skipSiblingsForward(@Nullable ASTNode node, @NotNull TokenSet types) {
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
  public static @Nullable ASTNode skipSiblingsBackward(@Nullable ASTNode node, @NotNull TokenSet types) {
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
  public static @Nullable PsiElement getFirstChildOfType(final @NotNull PsiElement element, @NotNull PyElementType type) {
    return PyPsiUtilsCore.getFirstChildOfType(element, type);
  }

  /**
   * Returns child element in the psi tree
   *
   * @param filter  Types of expected child
   * @param number  number
   * @param element tree parent node
   * @return PsiElement - child psiElement
   */
  public static @Nullable PsiElement getChildByFilter(@NotNull PsiElement element, @NotNull TokenSet filter, int number) {
    return PyPsiUtilsCore.getChildByFilter(element, filter, number);
  }

  public static void addBeforeInParent(final @NotNull PsiElement anchor, final PsiElement @NotNull ... newElements) {
    final ASTNode anchorNode = anchor.getNode();
    LOG.assertTrue(anchorNode != null);
    for (PsiElement newElement : newElements) {
      anchorNode.getTreeParent().addChild(newElement.getNode(), anchorNode);
    }
  }

  public static void removeElements(final PsiElement @NotNull ... elements) {
    final ASTNode parentNode = elements[0].getParent().getNode();
    LOG.assertTrue(parentNode != null);
    for (PsiElement element : elements) {
      parentNode.removeChild(element.getNode());
    }
  }

  public static @Nullable PsiElement getStatement(final @NotNull PsiElement element) {
    final PyElement compStatement = getStatementList(element);
    if (compStatement == null) {
      return null;
    }
    return getParentRightBefore(element, compStatement);
  }

  public static PyElement getStatementList(final PsiElement element) {
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
  public static @Nullable PsiElement getParentRightBefore(@NotNull PsiElement element, final @NotNull PsiElement superParent) {
    return PsiTreeUtil.findFirstParent(element, false, element1 -> element1.getParent() == superParent);
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
    if (parent instanceof PyFunction && PsiTreeUtil.getParentOfType(parent, PyFile.class, PyFunction.class, PyClass.class) instanceof PyClass) {
      return true;
    }
    return false;
  }


  public static @NotNull PsiElement getRealContext(final @NotNull PsiElement element) {
    assertValid(element);
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyExpressionCodeFragment fragment) {
      final PsiElement context = fragment.getRealContext();
      return context != null ? context : element;
    }
    else {
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
  public static @NotNull List<PsiComment> getPrecedingComments(@NotNull PsiElement element) {
    return getPrecedingComments(element, true);
  }

  public static @NotNull List<PsiComment> getPrecedingComments(@NotNull PsiElement element, boolean stopAtBlankLine) {
    return getPrecedingCommentsAndAnchor(element, stopAtBlankLine, true).getFirst();
  }

  private static @NotNull Pair<List<PsiComment>, PsiElement> getPrecedingCommentsAndAnchor(PsiElement element, boolean stopAtBlankLine,
                                                                                           boolean strict) {
    final ArrayList<PsiComment> result = new ArrayList<>();
    PsiElement cursor = element instanceof PsiComment && !strict ? element : element.getPrevSibling();
    while (true) {
      int newLinesCount = 0;
      while (cursor instanceof PsiWhiteSpace) {
        newLinesCount += StringUtil.getLineBreakCount(cursor.getText());
        cursor = cursor.getPrevSibling();
      }
      if ((stopAtBlankLine && newLinesCount > 1) || !(cursor instanceof PsiComment)) {
        break;
      }
      else {
        result.add((PsiComment)cursor);
      }
      cursor = cursor.getPrevSibling();
    }
    Collections.reverse(result);
    return Pair.create(result, cursor);
  }

  /**
   * Return blank-line-separated blocks of consecutive comments preceding the given element.
   * <p>
   * For instance, for the following fragment, it will return two blocks of one and two comments.
   *
   * <pre>{@code
   * # comment
   *
   * # comment
   * # comment
   * def func():
   *     pass
   * }</pre>
   *
   * Note that in the following case it will additionally return an empty list of comments as the last element
   * to distinguish between the cases when there is a blank line above the provided element and when there is not.
   *
   * <pre>{@code
   * # comment
   *
   * def func():
   *     pass
   * }</pre>
   *
   */
  public static @NotNull List<List<PsiComment>> getPrecedingCommentBlocks(@NotNull PsiElement element) {
    List<List<PsiComment>> blocks = new ArrayList<>();
    PsiElement anchor = element;
    do {
      Pair<List<PsiComment>, PsiElement> blockAndAnchor = getPrecedingCommentsAndAnchor(anchor, true, false);
      anchor = blockAndAnchor.getSecond();
      List<PsiComment> block = blockAndAnchor.getFirst();
      if (!block.isEmpty() || anchor instanceof PsiComment) {
        blocks.add(block);
      }
    }
    while (anchor instanceof PsiComment);
    Collections.reverse(blocks);
    return blocks;
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

  public static @Nullable PyTargetExpression getAttribute(final @NotNull PyFile file, final @NotNull String name) {
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

  public static @NotNull List<PyExpression> getAttributeValuesFromFile(@NotNull PyFile file, @NotNull String name) {
    List<PyExpression> result = new ArrayList<>();
    final PyTargetExpression attr = file.findTopLevelAttribute(name);
    if (attr != null) {
      sequenceToList(result, attr.findAssignedValue());
    }
    return result;
  }

  public static void sequenceToList(List<? super PyExpression> result, PyExpression value) {
    value = flattenParens(value);
    if (value instanceof PySequenceExpression) {
      ContainerUtil.addAll(result, ((PySequenceExpression)value).getElements());
    }
    else {
      result.add(value);
    }
  }

  public static List<String> getStringValues(PyExpression[] elements) {
    List<String> results = new ArrayList<>();
    for (PyExpression element : elements) {
      if (element instanceof PyStringLiteralExpression) {
        results.add(((PyStringLiteralExpression)element).getStringValue());
      }
    }
    return results;
  }

  public static @Nullable PyExpression flattenParens(@Nullable PyExpression expr) {
    return (PyExpression)PyPsiUtilsCore.flattenParens(expr);
  }

  public static @Nullable String strValue(@Nullable PyExpression expression) {
    return PyPsiUtilsCore.strValue(expression);
  }

  public static boolean isBefore(final @NotNull PsiElement element, final @NotNull PsiElement element2) {
    // TODO: From RubyPsiUtil, should be moved to PsiTreeUtil
    return element.getTextOffset() <= element2.getTextOffset();
  }

  public static @Nullable QualifiedName asQualifiedName(@Nullable PyExpression expr) {
    return PyPsiUtilsCore.asQualifiedName(expr);
  }

  public static @NotNull PyExpression getFirstQualifier(@NotNull PyQualifiedExpression expr) {
    final PyExpression qualifier = expr.getQualifier();
    if (qualifier instanceof PyQualifiedExpression) {
      return getFirstQualifier((PyQualifiedExpression)qualifier);
    }
    return expr;
  }

  public static @NotNull String toPath(@Nullable PyQualifiedExpression expr) {
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

  public static @Nullable QualifiedName asQualifiedName(@NotNull PyQualifiedExpression expr) {
    return PyPsiUtilsCore.asQualifiedName(expr);
  }

  /**
   * Wrapper for {@link PsiUtilCore#ensureValid(PsiElement)} that skips nulls
   */
  public static void assertValid(final @Nullable PsiElement element) {
    PyPsiUtilsCore.assertValid(element);
  }

  public static void assertValid(final @NotNull Module module) {
    LOG.assertTrue(!module.isDisposed(), String.format("Module %s is disposed", module));
  }

  public static @Nullable PsiFileSystemItem getFileSystemItem(@NotNull PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return (PsiFileSystemItem)element;
    }
    return element.getContainingFile();
  }

  public static @Nullable String getContainingFilePath(@NotNull PsiElement element) {
    final VirtualFile file;
    if (element instanceof PsiFileSystemItem) {
      file = ((PsiFileSystemItem)element).getVirtualFile();
    }
    else {
      file = element.getContainingFile().getVirtualFile();
    }
    if (file != null) {
      return FileUtil.toSystemDependentName(file.getPath());
    }
    return null;
  }

  /**
   * Checks if specified file contains passed source in top-level import in stub-safe way.
   * Does not process scopes inside the file.
   *
   * @param file   file whose imports should be visited
   * @param source qualified name separated by dots that is looking for in imports
   * @return true if specified file contains passed source in top-level import.
   */
  public static boolean containsImport(@NotNull PyFile file, @NotNull String source) {
    final QualifiedName sourceQName = QualifiedName.fromDottedString(source);

    return Stream.concat(
        file.getFromImports().stream().map(PyFromImportStatement::getImportSourceQName),
        file.getImportTargets().stream().map(PyImportElement::getImportedQName)
      )
      .filter(Objects::nonNull)
      .anyMatch(name -> name.matchesPrefix(sourceQName));
  }

  /**
   * Returns text of the given PSI element. Unlike obvious {@link PsiElement#getText()} this method unescapes text of the element if latter
   * belongs to injected code fragment using {@link InjectedLanguageManager#getUnescapedText(PsiElement)}.
   *
   * @param element PSI element which text is needed
   * @return text of the element with any host escaping removed
   */
  public static @NotNull String getElementTextWithoutHostEscaping(@NotNull PsiElement element) {
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(element.getProject());
    if (manager.isInjectedFragment(element.getContainingFile())) {
      return manager.getUnescapedText(element);
    }
    else {
      return element.getText();
    }
  }

  public static @Nullable String getStringValue(@Nullable PsiElement o) {
    if (o == null) {
      return null;
    }
    if (o instanceof PyStringLiteralExpression literalExpression) {
      return literalExpression.getStringValue();
    }
    else {
      return o.getText();
    }
  }

  public static TextRange getStringValueTextRange(PsiElement element) {
    if (element instanceof PyStringLiteralExpression) {
      final List<TextRange> ranges = ((PyStringLiteralExpression)element).getStringValueTextRanges();
      return ranges.get(0);
    }
    else {
      return new TextRange(0, element.getTextLength());
    }
  }

  public static @Nullable PsiComment findSameLineComment(@NotNull PsiElement elem) {
    // If `elem` is a compound multi-line element, stick to its first line nonetheless
    PsiElement next = PsiTreeUtil.getDeepestFirst(elem);
    do {
      if (next instanceof PsiComment) {
        return (PsiComment)next;
      }
      if (next != elem && next.textContains('\n')) {
        break;
      }
      next = PsiTreeUtil.nextLeaf(next);
    }
    while (next != null);
    return null;
  }
}
