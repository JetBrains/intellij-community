/*
 * @author max
 */
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyPsiUtils {
  public static final Key<Pair<PsiElement, TextRange>> SELECTION_BREAKS_AST_NODE =
    new Key<Pair<PsiElement, TextRange>>("python.selection.breaks.ast.node");
  private static final Logger LOG = Logger.getInstance(PyPsiUtils.class.getName());

  private PyPsiUtils() {
  }

  protected static <T extends PyElement> T[] nodesToPsi(ASTNode[] nodes, T[] array) {
    T[] psiElements = (T[])java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), nodes.length);
    for (int i = 0; i < nodes.length; i++) {
      //noinspection unchecked
      psiElements[i] = (T)nodes[i].getPsi();
    }
    return psiElements;
  }

  @Nullable
  protected static ASTNode getPrevComma(ASTNode after) {
    ASTNode node = after;
    PyElementType comma = PyTokenTypes.COMMA;
    do {
      node = node.getTreePrev();
    }
    while (node != null && !node.getElementType().equals(comma));
    return node;
  }

  @Nullable
  protected static ASTNode getNextComma(ASTNode after) {
    ASTNode node = after;
    PyElementType comma = PyTokenTypes.COMMA;
    do {
      node = node.getTreeNext();
    }
    while (node != null && !node.getElementType().equals(comma));
    return node;
  }

  public static PsiElement replaceExpression(@NotNull final Project project,
                                             @NotNull final PsiElement oldExpression,
                                             @NotNull final PsiElement newExpression) {
    final Pair<PsiElement, TextRange> data = oldExpression.getUserData(SELECTION_BREAKS_AST_NODE);
    if (data != null) {
      final PsiElement parent = data.first;
      final TextRange textRange = data.second;
      final String parentText = parent.getText();
      final String prefix = parentText.substring(0, textRange.getStartOffset());
      final String suffix = parentText.substring(textRange.getEndOffset(), parent.getTextLength());
      final PsiElement expression = PythonLanguage.getInstance().getElementGenerator()
        .createFromText(project, parent.getClass(), prefix + newExpression.getText() + suffix);
      return parent.replace(expression);
    }
    else {
      return oldExpression.replace(newExpression);
    }
  }

  public static void addToEnd(@NotNull final PsiElement psiElement, @NotNull final PsiElement... newElements) {
    final ASTNode psiNode = psiElement.getNode();
    LOG.assertTrue(psiNode != null);
    for (PsiElement newElement : newElements) {
      //noinspection ConstantConditions
      psiNode.addChild(newElement.getNode());
    }
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
    final PyElement compStatement = getCompoundStatement(element);
    if (compStatement == null){
      return null;
    }
    return getStatement(compStatement, element);
  }

  public static PyElement getCompoundStatement(final PsiElement element) {
    //noinspection ConstantConditions
    return element instanceof PyFile || element instanceof PyStatementList
           ? (PyElement) element
           : PsiTreeUtil.getParentOfType(element, PyFile.class, PyStatementList.class);
  }

  @Nullable
  public static PsiElement getStatement(final PsiElement compStatement, PsiElement element) {
    PsiElement parent = element.getParent();
    while (parent != null && parent != compStatement){
      element = parent;
      parent = element.getParent();
    }
    return parent != null ? element : null;
  }

  public static List<PsiElement> collectElements(final PsiElement statement1, final PsiElement statement2) {
    // Process ASTNodes here to handle all the nodes
    final ASTNode node1 = statement1.getNode();
    final ASTNode node2 = statement2.getNode();
    final ASTNode parentNode = node1.getTreeParent();

    boolean insideRange = false;
    final List<PsiElement> result = new ArrayList<PsiElement>();
    for (ASTNode node : parentNode.getChildren(null)) {
      // start
      if (node1 == node){
        insideRange = true;
      }
      if (insideRange){
        result.add(node.getPsi());
      }
      // stop
      if (node == node2){
        insideRange = false;
        break;
      }
    }
    return result;
  }

  public static int getElementIndentation(final PsiElement element){
    final PsiElement compStatement = getCompoundStatement(element);
    final PsiElement statement = getStatement(compStatement, element);
    PsiElement sibling = statement.getPrevSibling();
    if (sibling == null){
      sibling = compStatement.getPrevSibling();
    }
    final String whitespace = sibling instanceof PsiWhiteSpace ? sibling.getText() : "";
    final int i = whitespace.lastIndexOf("\n");
    return i != -1 ? whitespace.length() - i - 1 : 0;
  }

  /**
   * Creates copy of element without redundant whitespaces within element
   * @param element
   * @return
   */
  @NotNull
  public static PyElement removeIndentation(final PsiElement element) {
    final int indentLength = getElementIndentation(element);
    if (indentLength == 0 && element instanceof PyElement) {
      return (PyElement) element;
    }
    final String indentString = StringUtil.repeatSymbol(' ', indentLength);
    final String text = element.getText();
    final StringBuilder builder = new StringBuilder();
    for (String line : StringUtil.split(text, "\n")) {
      if (builder.length() != 0){
        builder.append("\n");
      }
      if (!StringUtil.isEmptyOrSpaces(line)){
        if (line.startsWith(indentString)){
          builder.append(line.substring(indentLength));
        } else {
          builder.append(line);
        }
      }
    }
    final PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    final PyElement result = elementGenerator.createFromText(element.getProject(), PyElement.class, builder.toString());
    if (result == null) {
      throw new RuntimeException("Failed to create element from text " + builder.toString());
    }
    return result;
  }

  public static void removeRedundantPass(final PyStatementList statementList) {
    final PyStatement[] statements = statementList.getStatements();
    if ((statements.length > 1) && (statements[0] instanceof PyPassStatement)) {
      statements[0].delete();
    }
  }

  public static boolean isMethodContext(final PsiElement element) {
    final PsiNamedElement parent = PsiTreeUtil.getParentOfType(element, PyFile.class, PyFunction.class, PyClass.class);
    // In case if element is inside method which is inside class
    if (parent instanceof PyFunction && PsiTreeUtil.getParentOfType(parent, PyFile.class, PyClass.class) instanceof PyClass){
      return true;
    }
    return false;
  }


  @NotNull
  public static PsiElement getRealContext(@NotNull final PsiElement element) {
    if (!element.isValid()){
      if (LOG.isDebugEnabled()){
        LOG.debug("PyPsiUtil.getRealContext(" + element + ") called. Returned null. Element in invalid");
      }
      return element;
    }
    final PsiFile file = element.getContainingFile();
    if (file != null) {
      final PsiElement context = file.getCopyableUserData(PyExpressionCodeFragmentImpl.CONTEXT_KEY);
      if (context != null){
        return context;
      }
    }
    if (file instanceof PyExpressionCodeFragment) {
      final PsiElement context = file.getContext();
      if (LOG.isDebugEnabled()){
        LOG.debug("PyPsiUtil.getRealContext(" + element + ") is called. Returned " + context +". Element inside code fragment");
      }
      return context != null ? context : element;
    }
    else {
      if (LOG.isDebugEnabled()){
        LOG.debug("PyPsiUtil.getRealContext(" + element + ") is called. Returned " + element +".");
      }
      return element;
    }
  }
}