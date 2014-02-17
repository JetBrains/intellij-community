package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.refactoring.RefactoringBundle;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.ui.PyClassCellRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Plugin that moves superclasses from one class to another
 *
 * @author Ilya.Kazakevich
 */
class SuperClassesManager extends MembersManager<PyClass> {

  private static final NoFakeSuperClasses NO_FAKE_SUPER_CLASSES = new NoFakeSuperClasses();

  SuperClassesManager() {
    super(PyClass.class);
  }


  @NotNull
  @Override
  protected List<PyElement> getMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    return Lists.<PyElement>newArrayList(Collections2.filter(Arrays.asList(pyClass.getSuperClasses()), NO_FAKE_SUPER_CLASSES));
  }

  @Override
  protected Collection<PyElement> moveMembers(@NotNull final PyClass from,
                                              @NotNull final Collection<PyMemberInfo<PyClass>> members,
                                              @NotNull final PyClass... to) {
    final Collection<PyClass> elements = fetchElements(members);
    for (final PyClass destClass : to) {
      PyClassRefactoringUtil.addSuperclasses(from.getProject(), destClass, elements.toArray(new PyClass[members.size()]));
    }

    for (final PyExpression expression : from.getSuperClassExpressions()) {
      // Remove all superclass expressions that point to class from memberinfo
      if (!(expression instanceof PyQualifiedExpression)) {
        continue;
      }
      final PyReferenceExpression reference = (PyReferenceExpression)expression;
      for (final PyClass element : elements) {
        if (reference.getReference().isReferenceTo(element)) {
          expression.delete();
        }
      }
    }
    return Collections.emptyList(); //Hack: we know that "superclass expression" can't have reference
  }

  @NotNull
  @Override
  public PyMemberInfo<PyClass> apply(@NotNull final PyClass input) {
    final String name = RefactoringBundle.message("member.info.extends.0", PyClassCellRenderer.getClassText(input));
    //TODO: Check for "overrides"
    return new PyMemberInfo<PyClass>(input, false, name, false, this, false);
  }

  private static class NoFakeSuperClasses extends NotNullPredicate<PyClass> {
    @Override
    protected boolean applyNotNull(@NotNull final PyClass input) {
      return !PyNames.FAKE_OLD_BASE.equals(input.getName());
    }
  }
}
