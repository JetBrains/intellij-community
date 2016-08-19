package com.jetbrains.python.refactoring.move;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyMoveModuleMembersHelper {
  private PyMoveModuleMembersHelper() {
    // Utility class
  }

  /**
   * Checks that given element is suitable for the "Move" refactoring. Currently it means that it's either top-level function, class or
   * target expression (global variable, constant).
   *
   * @param element PSI element to check
   * @return whether this element is acceptable for "Move ..." refactoring
   */
  public static boolean isMovableModuleMember(@NotNull PsiElement element) {
    if (!(hasMovableElementType(element) && PyUtil.isTopLevel(element))) {
      return false;
    }
    if (element instanceof PyTargetExpression) {
      return !(PyNames.ALL.equals(((PyTargetExpression)element).getName())) && isTargetOfSimpleAssignment(element);
    }
    return true;
  }

  public static boolean hasMovableElementType(@NotNull PsiElement element) {
    return element instanceof PyClass || element instanceof PyFunction || element instanceof PyTargetExpression;
  }

  /**
   * Checks that given element is target of the simplest Python assignment suitable for "Move" refactoring:
   * exactly it's unqualified name in the right-hand side of assignment statement with single target, e.g. {@code CONST} in {@code CONST = 42}.
   * There should be neither unpacking, nor chained assignment, nor clarifying parenthesis in assignment target.
   * <p/>
   * Such target expression at the top-level of its module can be treated as definition of global variable (supposedly constant).
   *
   * @param element PSI element to check
   */
  public static boolean isTargetOfSimpleAssignment(@NotNull PsiElement element) {
    final PyTargetExpression target = as(element, PyTargetExpression.class);
    if (target == null || target.isQualified()) {
      return false;
    }
    final PyAssignmentStatement assignment = as(target.getParent(), PyAssignmentStatement.class);
    return assignment != null && assignment.getTargets().length == 1;
  }

  /**
   * Collects all movable top-level variables, classes and functions (in this order) as returned by {@link PyFile#getTopLevelAttributes()},
   * {@link PyFile#getTopLevelClasses()} and {@link PyFile#getTopLevelFunctions()}. Target expression are filtered with
   * {@link #isTargetOfSimpleAssignment(PsiElement)}.
   */
  public static List<PyElement> getTopLevelModuleMembers(@NotNull PyFile pyFile) {
    final List<PyElement> result = new ArrayList<>();
    for (PyTargetExpression attr : pyFile.getTopLevelAttributes()) {
      if (isMovableModuleMember(attr)) {
        result.add(attr);
      }
    }
    result.addAll(pyFile.getTopLevelClasses());
    result.addAll(pyFile.getTopLevelFunctions());
    return result;
  }

  /**
   * Expands given named element to the closet parent suitable for "Move" refactoring. In particular for target expression
   * it returns parental assignment statement if any and element itself for functions and classes.
   *
   * @see #extractNamedElement(PsiElement)
   */
  @Nullable
  public static PsiElement expandNamedElementBody(@NotNull PsiNamedElement element) {
    if (element instanceof PyClass || element instanceof PyFunction) {
      return element;
    }
    else if (element instanceof PyTargetExpression && element.getParent() instanceof PyAssignmentStatement) {
      return element.getParent();
    }
    return null;
  }

  /**
   * Performs operation opposite to the {@link #expandNamedElementBody}, in particular it shrinks assignment statement back
   * to the first target expression.
   *
   * @see #expandNamedElementBody(PsiNamedElement)
   */
  @Nullable
  public static PsiNamedElement extractNamedElement(@NotNull PsiElement element) {
    if (element instanceof PyClass || element instanceof PyFunction || element instanceof PyTargetExpression) {
      return (PsiNamedElement)element;
    }
    final PyAssignmentStatement assignment = as(element, PyAssignmentStatement.class);
    if (assignment != null) {
      return as(assignment.getTargets()[0], PyTargetExpression.class);
    }
    return null;
  }
}
