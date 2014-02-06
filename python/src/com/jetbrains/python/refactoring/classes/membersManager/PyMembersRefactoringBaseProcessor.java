package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
public abstract class PyMembersRefactoringBaseProcessor extends BaseRefactoringProcessor implements UsageViewDescriptor {

  @NotNull
  protected final Collection<PyMemberInfo> myMembersToMove;
  @NotNull
  protected final PyClass myTo;
  @NotNull
  protected final PyClass myFrom;

  protected PyMembersRefactoringBaseProcessor(@NotNull final PyClass from,
                                              @NotNull final PyClass to,
                                              @NotNull final Collection<PyMemberInfo> membersToMove) {
    super(from.getProject());
    myFrom = from;
    myTo = to;
    myMembersToMove = new ArrayList<PyMemberInfo>(membersToMove);
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return this;
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    final List<PyUsageInfo> result = new ArrayList<PyUsageInfo>(myMembersToMove.size());
    for (final PyMemberInfo pyMemberInfo : myMembersToMove) {
      result.add(new PyUsageInfo(pyMemberInfo));
    }
    return result.toArray(new UsageInfo[result.size()]);
  }

  @Override
  protected void performRefactoring(final UsageInfo[] usages) {
    final Collection<PyMemberInfo> membersToMoveFromUsage = new ArrayList<PyMemberInfo>(usages.length);
    for (final UsageInfo usage : usages) {
      if (!(usage instanceof PyUsageInfo)) {
        throw new IllegalArgumentException("Only PyUsageInfo is accepted here");
      }
      //TODO: Doc
      membersToMoveFromUsage.add(((PyUsageInfo)usage).getPyMemberInfo());
    }
    MembersManager.moveAllMembers(myFrom, myTo, membersToMoveFromUsage);
  }
}
