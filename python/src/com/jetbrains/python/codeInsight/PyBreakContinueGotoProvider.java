package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.ParentMatcher;

import java.util.List;

/**
 * Provides reaction on ctrl+click for {@code break} and {@code continue} statements.
 * User: dcheryasov
 * Date: Nov 5, 2009 4:58:54 AM
 */
public class PyBreakContinueGotoProvider extends GotoDeclarationHandlerBase {

  public PsiElement getGotoDeclarationTarget(PsiElement source) {
    if (source != null && source.getLanguage() instanceof PythonLanguage) {
      ParentMatcher parent_matcher = new ParentMatcher(PyForStatement.class, PyWhileStatement.class);
      parent_matcher.limitBy(PyFunction.class, PyClass.class);
      List<? extends PsiElement> cycle_list = parent_matcher.search(source);
      if (cycle_list != null && cycle_list.size() == 1) {
        PsiElement cycle_element = cycle_list.get(0);
        ASTNode node = source.getNode();
        if (node != null) {
          IElementType node_type = node.getElementType();
          if (node_type == PyTokenTypes.CONTINUE_KEYWORD) {
            return cycle_element;
          }
          if (node_type == PyTokenTypes.BREAK_KEYWORD) {
            PsiElement outer_element = cycle_element;
            PsiElement after_cycle;
            while (true) {
              after_cycle = outer_element.getNextSibling();
              if (after_cycle != null) {
                if (after_cycle instanceof PsiWhiteSpace) {
                  after_cycle = after_cycle.getNextSibling();
                }
                if (after_cycle instanceof PyStatement) return after_cycle;
              }
              outer_element = outer_element.getParent();
              if (PyUtil.instanceOf(outer_element, PsiFile.class, PyFunction.class, PyClass.class)) {
                break;
              }
            }
            // cycle is the last statement in the flow of execution. its last element is our best bet.
            return PsiTreeUtil.getDeepestLast(cycle_element);
          }
        }
      }
    }
    return null;
  }
}
