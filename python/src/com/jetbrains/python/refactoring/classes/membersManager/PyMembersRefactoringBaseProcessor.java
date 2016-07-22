/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    myMembersToMove = new ArrayList<>(membersToMove);
    myTo = to.clone();
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull final UsageInfo[] usages) {
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
    final List<PyUsageInfo> result = new ArrayList<>(myTo.length);
    for (final PyClass pyDestinationClass : myTo) {
      result.add(new PyUsageInfo(pyDestinationClass));
    }
    return result.toArray(new PyUsageInfo[result.size()]);
  }

  @Override
  protected final void performRefactoring(@NotNull final UsageInfo[] usages) {
    final Collection<PyClass> destinations = new ArrayList<>(usages.length);
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

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myFrom);
    data.addMembers(myMembersToMove.toArray(new PyMemberInfo[myMembersToMove.size()]), info -> info.getMember());
    return data;
  }


  @Nullable
  @Override
  protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages) {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElements(myTo);
    return data;
  }
}
