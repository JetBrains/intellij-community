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
package com.jetbrains.python.refactoring.move.moduleMembers;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.move.PyMoveRefactoringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Group found usages by moved elements and move each of these elements using {@link PyMoveSymbolProcessor}.
 *
 * @author vlan
 * @author Mikhail Golubev
 *
 * @see PyMoveSymbolProcessor
 */
public class PyMoveModuleMembersProcessor extends BaseRefactoringProcessor {
  private final List<SmartPsiElementPointer<PsiNamedElement>> myElements;
  private final LinkedHashSet<PsiFile> mySourceFiles;
  private final String myDestination;

  public PyMoveModuleMembersProcessor(PsiNamedElement @NotNull [] elements, @NotNull String destination) {
    super(elements[0].getProject());
    final SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
    myElements = ContainerUtil.map(elements, manager::createSmartPsiElementPointer);
    mySourceFiles = new LinkedHashSet<>(ContainerUtil.map(elements, PsiElement::getContainingFile));
    myDestination = destination;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo @NotNull [] usages) {
    return new UsageViewDescriptorAdapter() {
      @Override
      public PsiElement @NotNull [] getElements() {
        return ContainerUtil.mapNotNull(myElements, SmartPsiElementPointer::getElement).toArray(PsiElement.EMPTY_ARRAY);
      }

      @Override
      public String getProcessedElementsHeader() {
        return getRefactoringName();
      }
    };
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    return StreamEx.of(myElements)
      .map(SmartPsiElementPointer::getElement)
      .nonNull()
      .flatMap(e -> StreamEx.of(PyPsiIndexUtil.findUsages(e, false))
        .map(info -> new MyUsageInfo(info, e)))
      .toArray(UsageInfo[]::new);
  }

  @Override
  protected void performRefactoring(final UsageInfo @NotNull [] usages) {
    final MultiMap<PsiElement, UsageInfo> usagesByElement = MultiMap.create();
    for (UsageInfo usage : usages) {
      usagesByElement.putValue(((MyUsageInfo)usage).myMovedElement, usage);
    }
    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      final PyFile destination = PyClassRefactoringUtil.getOrCreateFile(myDestination, myProject);
      CommonRefactoringUtil.checkReadOnlyStatus(myProject, destination);
      final LinkedHashSet<PsiFile> optimizeImportsTargets = Sets.newLinkedHashSet(mySourceFiles);
      for (final SmartPsiElementPointer<PsiNamedElement> pointer : myElements) {
        // TODO: Check for resulting circular imports
        final PsiNamedElement e = pointer.getElement();
        if (e == null) {
          continue;
        }
        CommonRefactoringUtil.checkReadOnlyStatus(myProject, e);
        assert e instanceof PyClass || e instanceof PyFunction || e instanceof PyTargetExpression;
        final String name = e.getName();
        if (name == null) {
          continue;
        }
        if (e instanceof PyClass && destination.findTopLevelClass(name) != null) {
          throw new IncorrectOperationException(
            PyBundle.message("refactoring.move.error.destination.file.contains.class", name));
        }
        if (e instanceof PyFunction && destination.findTopLevelFunction(name) != null) {
          throw new IncorrectOperationException(
            PyBundle.message("refactoring.move.error.destination.file.contains.function", name));
        }
        if (e instanceof PyTargetExpression && destination.findTopLevelAttribute(name) != null) {
          throw new IncorrectOperationException(
            PyBundle.message("refactoring.move.error.destination.file.contains.global.variable", name));
        }
        final Collection<UsageInfo> usageInfos = usagesByElement.get(e);
        final boolean usedFromOutside = ContainerUtil.exists(usageInfos, usageInfo -> {
          final PsiElement element = usageInfo.getElement();
          return element != null && !PsiTreeUtil.isAncestor(e, element, false);
        });
        if (usedFromOutside) {
          PyMoveRefactoringUtil.checkValidImportableFile(e, destination.getVirtualFile());
        }
        final PyMoveSymbolResult results = new PyMoveSymbolProcessor(e, destination, usageInfos, myElements).moveElement();
        optimizeImportsTargets.addAll(results.getOptimizeImportsTargets());
      }

      for (PsiFile file : optimizeImportsTargets) {
        PyClassRefactoringUtil.optimizeImports(file);
      }
    }), getRefactoringName(), null);
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return getRefactoringName();
  }

  private static class MyUsageInfo extends UsageInfo {
    private final PsiElement myMovedElement;
    MyUsageInfo(@NotNull UsageInfo usageInfo, @NotNull PsiElement element) {
      super(usageInfo.getSmartPointer(), usageInfo.getPsiFileRange(), usageInfo.isDynamicUsage(), usageInfo.isNonCodeUsage, usageInfo.getReferenceClass());
      myMovedElement = element;
    }
  }

  public static @Nls String getRefactoringName() {
    return PyBundle.message("refactoring.move.module.members");
  }
}
