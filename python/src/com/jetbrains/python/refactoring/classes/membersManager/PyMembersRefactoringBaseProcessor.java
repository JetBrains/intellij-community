package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Processor for member-based refactorings. It moves members from one place to another using {@link com.jetbrains.python.refactoring.classes.membersManager.MembersManager}.
 * Inheritors only need to implement {@link com.intellij.usageView.UsageViewDescriptor} methods (while this interface is also implemented by this class)
 *
 * @author Ilya.Kazakevich
 */
public abstract class PyMembersRefactoringBaseProcessor extends BaseRefactoringProcessor implements UsageViewDescriptor {

  @NotNull
  protected final Collection<PyMemberInfo<PyElement>> myMembersToMove;
  @NotNull
  protected final PyClass myFrom;
  @NotNull
  private final PyClass[] myTo;

  /**
   * @param membersToMove what to move
   * @param from          source
   * @param to            where to move
   */
  protected PyMembersRefactoringBaseProcessor(
    @NotNull final Project project,
    @NotNull final Collection<PyMemberInfo<PyElement>> membersToMove,
    @NotNull final PyClass from,
    @NotNull final PyClass... to) {
    super(project);
    myFrom = from;
    myMembersToMove = new ArrayList<PyMemberInfo<PyElement>>(membersToMove);
    myTo = to.clone();
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return this;
  }

  @NotNull
  @Override
  public PsiElement[] getElements() {
    return myTo.clone();
  }

  /**
   * @return destinations (so user would be able to choose if she wants to move member to certain place or not)
   */
  @NotNull
  @Override
  protected final PyUsageInfo[] findUsages() {
    final List<PyUsageInfo> result = new ArrayList<PyUsageInfo>(myTo.length);
    for (final PyClass pyDestinationClass : myTo) {
      result.add(new PyUsageInfo(pyDestinationClass));
    }
    return result.toArray(new PyUsageInfo[result.size()]);
  }

  @Override
  protected final void performRefactoring(final UsageInfo[] usages) {
    final Collection<PyClass> destinations = new ArrayList<PyClass>(usages.length);
    for (final UsageInfo usage : usages) {
      if (!(usage instanceof PyUsageInfo)) {
        throw new IllegalArgumentException("Only PyUsageInfo is accepted here");
      }
      //We collect destination info to pass it to members manager
      destinations.add(((PyUsageInfo)usage).getTo());
    }
    MembersManager.moveAllMembers(myMembersToMove, myFrom, destinations.toArray(new PyClass[destinations.size()]));
    PyClassRefactoringUtil.optimizeImports(myFrom.getContainingFile()); // To remove unneeded imports
  }
}
