package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * TODO: Doc (and why it extends 2 classes)
 *
 * @author Ilya.Kazakevich
 */
public abstract class PyMembersRefactoringBaseProcessor extends BaseRefactoringProcessor implements UsageViewDescriptor {

  @NotNull
  protected final Collection<PyMemberInfo> myMembersToMove;
  @NotNull
  protected final PyClass myFrom;
  @NotNull
  private final PyClass[] myTo;

  //TODO: Doc
  protected PyMembersRefactoringBaseProcessor(
    @NotNull final Collection<PyMemberInfo> membersToMove,
    @NotNull final PyClass from,
    @NotNull final PyClass... to) {
    super(from.getProject());
    myFrom = from;
    myMembersToMove = new ArrayList<PyMemberInfo>(membersToMove);
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

  //TODO: Doc
  @NotNull
  @Override
  protected final PyUsageInfo[] findUsages() {
    final List<PyUsageInfo> result = new ArrayList<PyUsageInfo>(myTo.length);
    for (final PyClass pyDestinationClass : myTo) {
      result.add(new PyUsageInfo(pyDestinationClass));
    }
    return result.toArray(new PyUsageInfo[result.size()]);
  }

  //TODO: Doc
  @Override
  protected final void performRefactoring(final UsageInfo[] usages) {
    final Collection<PyClass> destinations = new ArrayList<PyClass>(usages.length);
    for (final UsageInfo usage : usages) {
      if (!(usage instanceof PyUsageInfo)) {
        throw new IllegalArgumentException("Only PyUsageInfo is accepted here");
      }
      //TODO: Doc
      destinations.add(((PyUsageInfo)usage).getTo());
    }
    MembersManager.moveAllMembers(myMembersToMove, myFrom, destinations.toArray(new PyClass[destinations.size()]));
  }
}
