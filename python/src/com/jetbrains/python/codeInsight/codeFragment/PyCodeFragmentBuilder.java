package com.jetbrains.python.codeInsight.codeFragment;

import com.intellij.codeInsight.codeFragment.CodeFragmentUtil;
import com.intellij.codeInsight.codeFragment.Position;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

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
  public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
    final PyExpression value = node.getAssignedValue();
    if (value != null) {
      value.accept(this);
    }
    for (PyExpression expression : node.getTargets()) {
      expression.accept(this);
    }
  }

  @Override
  public void visitPyAugAssignmentStatement(final PyAugAssignmentStatement node) {
    final PyExpression value = node.getValue();
    if (value != null) {
      value.accept(this);
    }
    final PyExpression target = node.getTarget();
    if (target instanceof PyReferenceExpression){
      visitPyReferenceExpression((PyReferenceExpression) target);
      processDeclaration(target);
    }
  }


  @Override
  public void visitPyTargetExpression(final PyTargetExpression node) {
    processDeclaration(node);
  }

  @Override
  public void visitPyNamedParameter(final PyNamedParameter node) {
    processDeclaration(node);
  }

  @Override
  public void visitPyReferenceExpression(final PyReferenceExpression element) {
    // Python PSI makes us to visit qualifier manually
    final PyExpression qualifier = element.getQualifier();
    if (qualifier != null){
      qualifier.accept(this);
      return;
    }
    // Process import references
    if (PyImportStatementNavigator.getImportStatementByElement(element) != null){
      processDeclaration(element);
      return;
    }
    // Ignore self as local variable usage in case of method context
    if (PyPsiUtils.isMethodContext(element) && "self".equals(element.getName())){
      return;
    }

    final Position position = CodeFragmentUtil.getPosition(element, startOffset, endOffset);
    final String name = element.getName();

    // Collect in variables
    if (position == Position.INSIDE) {
      for (ResolveResult result : element.getReference().multiResolve(false)) {
        final PsiElement declaration = result.getElement();
        // Ignore classes and methods declared somewhere else
        if ((declaration instanceof PyClass || declaration instanceof PyFunction) &&
            (!PsiTreeUtil.isAncestor(myOwner, declaration, false) ||
             CodeFragmentUtil.getPosition(declaration, startOffset, endOffset) != Position.INSIDE)){
          continue;
        }
        // Ignore outer elements or import statements
        if (declaration == null || !PsiTreeUtil.isAncestor(myOwner, declaration, false) ||
            declaration instanceof PyImportElement){
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
      for (ResolveResult result : element.getReference().multiResolve(false)) {
        final PsiElement declaration = result.getElement();
        // Handle resolve via import statement
        if (declaration instanceof PyFile && modifiedInsideMap.containsKey(name)){
          outElements.add(name);
          break;
        }
        // Ignore declarations out of scope
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
          if (!isTopLevel(element)) {
            inElements.add(name);
          }
          final List<PyElement> list = modifiedInsideMap.get(name);
          boolean modificationSeen = false;
          if (list != null) {
            for (PyElement modification : list) {
              final PsiReference reference = modification.getReference();
              if (reference != null && reference.isReferenceTo(declaration)) {
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

  private static boolean isTopLevel(@NotNull PyElement element) {
    return ScopeUtil.getScopeOwner(element) instanceof PyFile;
  }

  private void processDeclaration(final PyElement element) {
    final Position position = CodeFragmentUtil.getPosition(element, startOffset, endOffset);
    final String name = element.getName();
    // Collect in variables
    if (position == Position.INSIDE) {
      // support declarations within loops
      final PyLoopStatement loop = PsiTreeUtil.getParentOfType(element, PyLoopStatement.class);
      if (inElements.contains(name) && loop != null && CodeFragmentUtil.getPosition(loop, startOffset, endOffset) != Position.INSIDE) {
        outElements.add(name);
      }
      // Add modification inside
      List<PyElement> list = modifiedInsideMap.get(name);
      if (list == null) {
        list = new ArrayList<PyElement>();
        modifiedInsideMap.put(name, list);
      }
      list.add(element);
    }
  }
}
