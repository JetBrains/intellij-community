package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.classes.ui.PyClassCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Plugin that moves superclasses from one class to another
 *
 * @author Ilya.Kazakevich
 */
class SuperClassesManager extends MembersManager<PyClass> {
  SuperClassesManager() {
    super(PyClass.class);
  }

  private static final NameExtractor NAME_EXTRACTOR = new NameExtractor();
  private static final NameFilter NAME_FILTER = new NameFilter();

  @NotNull
  @Override
  protected List<PyElement> getMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    return Arrays.<PyElement>asList(pyClass.getSuperClasses());
  }

  @Override
  protected void moveMembers(@NotNull final PyClass from, @NotNull final PyClass to, @NotNull final Collection<PyClass> members) {
    final Set<String> superClassesToMove =
      Sets.newHashSet(Collections2.filter(Collections2.transform(members, NAME_EXTRACTOR), NAME_FILTER));

    for (final PyElement member : members) {
      superClassesToMove.add(member.getName());
    }

    PyClassRefactoringUtil.moveSuperclasses(from, superClassesToMove, to);
    //TODO: Use generics!
    PyClassRefactoringUtil.insertImport(to, new ArrayList<PsiNamedElement>(members));
  }

  @NotNull
  @Override
  public PyMemberInfo apply(@NotNull final PyElement input) {
    final String name = RefactoringBundle.message("member.info.extends.0", PyClassCellRenderer.getClassText((PyClass)input));
    //TODO: Check for "overrides"
    return new PyMemberInfo(input, false, name, false, this);
  }

  private static class NameExtractor implements Function<PyElement, String> {
    @SuppressWarnings("NullableProblems") //We sure collection has no null
    @Nullable
    @Override
    public String apply(@NotNull final PyElement input) {
      return input.getName();
    }
  }

  private static class NameFilter implements Predicate<String> {
    @Override
    public boolean apply(@Nullable final String input) {
      return input != null;
    }
  }
}
