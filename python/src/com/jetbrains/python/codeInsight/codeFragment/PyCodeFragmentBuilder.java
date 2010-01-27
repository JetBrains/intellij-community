package com.jetbrains.python.codeInsight.codeFragment;

import com.intellij.codeInsight.codeFragment.CodeFragmentUtil;
import com.intellij.codeInsight.codeFragment.Position;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;

import java.util.*;

/**
 * @author oleg
 */
public class PyCodeFragmentBuilder extends PyRecursiveElementVisitor {
  final Map<String, List<PyElement>> modifiedInsideMap = new HashMap<String, List<PyElement>>();

  final Set<String> inElements = new HashSet<String>();
  final Set<String> outElements = new HashSet<String>();

  private final ScopeOwner myOwner;
  private final int startOffset;
  private final int endOffset;

  public PyCodeFragmentBuilder(final ScopeOwner owner, final int start, final int end) {
    myOwner = owner;
    startOffset = start;
    endOffset = end;
  }

  @Override
  public void visitPyTargetExpression(final PyTargetExpression node) {
    visitDeclaration(node);
  }

  @Override
  public void visitPyNamedParameter(final PyNamedParameter node) {
    visitDeclaration(node);
  }

  @Override
  public void visitPyReferenceExpression(final PyReferenceExpression element) {
    final Position position = CodeFragmentUtil.getPosition(element, startOffset, endOffset);
    final String name = element.getName();

    // Collect in variables
    if (position == Position.INSIDE) {
      for (ResolveResult result : element.multiResolve(false)) {
        final PsiElement declaration = result.getElement();
        if (declaration == null || !PsiTreeUtil.isAncestor(myOwner, declaration, false)){
          continue;
        }
        final Position pos = CodeFragmentUtil.getPosition(declaration, startOffset, endOffset);
        // If declaration is before add it to input
        if (pos == Position.BEFORE) {
          inElements.add(name);
          break;
        }
      }
    }

    // Collect out variables
    if (position == Position.AFTER) {
      // if name is already in out parameters
      if (outElements.contains(name)) {
        return;
      }
      for (ResolveResult result : element.multiResolve(false)) {
        final PsiElement declaration = result.getElement();
        if (declaration == null || !PsiTreeUtil.isAncestor(myOwner, declaration, false)){
          continue;
        }
        final Position pos = CodeFragmentUtil.getPosition(declaration, startOffset, endOffset);
        // If declaration is inside
        if (pos == Position.INSIDE) {
          outElements.add(name);
          break;
        }
        // If declaration is before we look for modifications inside
        if (pos == Position.BEFORE) {
          final List<PyElement> list = modifiedInsideMap.get(name);
          boolean modificationSeen = false;
          if (list != null) {
            for (PyElement modification : list) {
              if (modification.getReference().isReferenceTo(declaration)) {
                outElements.add(name);
                modificationSeen = true;
                break;
              }
            }
            if (modificationSeen) {
              break;
            }
          }
        }
      }
    }
  }

  private void visitDeclaration(final PyElement element) {
    final Position position = CodeFragmentUtil.getPosition(element, startOffset, endOffset);
    final String name = element.getName();

    // Collect in variables
    if (position == Position.INSIDE) {

      // Add modification inside
      List<PyElement> list = modifiedInsideMap.get(name);
      if (list == null) {
        list = new ArrayList<PyElement>();
        modifiedInsideMap.put(name, list);
      }
      list.add(element);
    }

    // if name is already in out parameters
    if (inElements.contains(name)) {
      return;
    }
  }
}
