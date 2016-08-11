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
package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Group found usages by moved elements and move each of these elements using {@link PyMoveSymbolProcessor}.
 *
 * attribute's
 * @author vlan
 * @author Mikhail Golubev
 *
 * @see PyMoveSymbolProcessor
 */
public class PyMoveModuleMembersProcessor extends BaseRefactoringProcessor {
  public static final String REFACTORING_NAME = PyBundle.message("refactoring.move.module.members");

  private final PsiNamedElement[] myElements;
  private final String myDestination;

  public PyMoveModuleMembersProcessor(Project project, PsiNamedElement[] elements, String destination, boolean previewUsages) {
    super(project);
    assert elements.length > 0;
    myElements = elements;
    myDestination = destination;
    setPreviewUsages(previewUsages);
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull final UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return myElements;
      }

      @Override
      public String getProcessedElementsHeader() {
        return REFACTORING_NAME;
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> result = new ArrayList<>();
    for (final PsiNamedElement element : myElements) {
      result.addAll(ContainerUtil.map(PyRefactoringUtil.findUsages(element, false), usageInfo -> new MyUsageInfo(usageInfo, element)));
    }
    return result.toArray(new UsageInfo[result.size()]);
  }

  @Override
  protected void performRefactoring(@NotNull final UsageInfo[] usages) {
    final MultiMap<PsiElement, UsageInfo> usagesByElement = MultiMap.create();
    for (UsageInfo usage : usages) {
      usagesByElement.putValue(((MyUsageInfo)usage).myMovedElement, usage);
    }
    CommandProcessor.getInstance().executeCommand(myElements[0].getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      final PyFile destination = PyUtil.getOrCreateFile(myDestination, myProject);
      CommonRefactoringUtil.checkReadOnlyStatus(myProject, destination);
      for (final PsiNamedElement e : myElements) {
        // TODO: Check for resulting circular imports
        CommonRefactoringUtil.checkReadOnlyStatus(myProject, e);
        assert e instanceof PyClass || e instanceof PyFunction || e instanceof PyTargetExpression;
        if (e instanceof PyClass && destination.findTopLevelClass(e.getName()) != null) {
          throw new IncorrectOperationException(
            PyBundle.message("refactoring.move.module.members.error.destination.file.contains.class.$0", e.getName()));
        }
        if (e instanceof PyFunction && destination.findTopLevelFunction(e.getName()) != null) {
          throw new IncorrectOperationException(
            PyBundle.message("refactoring.move.module.members.error.destination.file.contains.function.$0", e.getName()));
        }
        if (e instanceof PyTargetExpression && destination.findTopLevelAttribute(e.getName()) != null) {
          throw new IncorrectOperationException(
            PyBundle.message("refactoring.move.module.members.error.destination.file.contains.global.variable.$0", e.getName()));
        }
        final Collection<UsageInfo> usageInfos = usagesByElement.get(e);
        final boolean usedFromOutside = ContainerUtil.exists(usageInfos, usageInfo -> {
          final PsiElement element = usageInfo.getElement();
          return element != null && !PsiTreeUtil.isAncestor(e, element, false);
        });
        if (usedFromOutside) {
          checkValidImportableFile(e, destination.getVirtualFile());
        }
        new PyMoveSymbolProcessor(e, destination, usageInfos, myElements).moveElement();
      }
    }), REFACTORING_NAME, null);
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return REFACTORING_NAME;
  }

  /**
   * Additionally contains referenced element.
   */
  private static void checkValidImportableFile(PsiElement anchor, VirtualFile file) {
    final QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(anchor, file);
    if (!PyClassRefactoringUtil.isValidQualifiedName(qName)) {
      throw new IncorrectOperationException(PyBundle.message("refactoring.move.module.members.error.cannot.use.module.name.$0",
                                                             file.getName()));
    }
  }

  private static class MyUsageInfo extends UsageInfo {
    private final PsiElement myMovedElement;
    public MyUsageInfo(@NotNull UsageInfo usageInfo, @NotNull PsiElement element) {
      super(usageInfo.getSmartPointer(), usageInfo.getPsiFileRange(), usageInfo.isDynamicUsage(), usageInfo.isNonCodeUsage);
      myMovedElement = element;
    }
  }
}

