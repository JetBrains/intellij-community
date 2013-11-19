/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.documentation.DocStringTypeReference;
import com.jetbrains.python.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.jetbrains.python.psi.impl.PyImportStatementNavigator.getImportStatementByElement;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionProcessor extends BaseRefactoringProcessor {
  public static final String REFACTORING_NAME = PyBundle.message("refactoring.move.class.or.function");

  private PsiNamedElement[] myElements;
  private String myDestination;

  public PyMoveClassOrFunctionProcessor(Project project, PsiNamedElement[] elements, String destination, boolean previewUsages) {
    super(project);
    assert elements.length > 0;
    myElements = elements;
    myDestination = destination;
    setPreviewUsages(previewUsages);
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
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
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    for (PsiNamedElement element : myElements) {
      usages.addAll(PyRefactoringUtil.findUsages(element, false));
    }
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  @Override
  protected void performRefactoring(final UsageInfo[] usages) {
    CommandProcessor.getInstance().executeCommand(myElements[0].getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final PyFile destination = PyUtil.getOrCreateFile(myDestination, myProject);
            CommonRefactoringUtil.checkReadOnlyStatus(myProject, destination);
            for (PsiNamedElement e: myElements) {
              // TODO: Check for resulting circular imports
              CommonRefactoringUtil.checkReadOnlyStatus(myProject, e);
              assert e instanceof PyClass || e instanceof PyFunction;
              if (e instanceof PyClass && destination.findTopLevelClass(e.getName()) != null) {
                throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.destination.file.contains.class.$0",
                                                                       e.getName()));
              }
              if (e instanceof PyFunction && destination.findTopLevelFunction(e.getName()) != null) {
                throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.destination.file.contains.function.$0",
                                                                       e.getName()));
              }
              checkValidImportableFile(destination, e.getContainingFile().getVirtualFile());
              checkValidImportableFile(e, destination.getVirtualFile());
            }
            for (PsiNamedElement oldElement: myElements) {
              moveElement(oldElement, Arrays.asList(usages), destination);
            }
          }
        });
      }
    }, REFACTORING_NAME, null);
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return REFACTORING_NAME;
  }

  private static void moveElement(@NotNull PsiNamedElement element, @NotNull Collection<UsageInfo> usages, @NotNull PyFile destination) {
    final PsiFile file = element.getContainingFile();
    PyClassRefactoringUtil.rememberNamedReferences(element);
    final PsiNamedElement newElement = addToFile(element, destination, usages);
    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement != null) {
        updateUsage(usageElement, element, newElement);
      }
    }
    PyClassRefactoringUtil.restoreNamedReferences(newElement, element);
    // TODO: Remove extra empty lines after the removed element
    element.delete();
    if (file != null) {
      optimizeImports(file);
    }
  }

  private static PsiNamedElement addToFile(@NotNull PsiNamedElement element, @NotNull final PyFile destination,
                                           @NotNull Collection<UsageInfo> usages) {
    List<PsiElement> topLevelAtDestination = new ArrayList<PsiElement>();
    for (UsageInfo usage : usages) {
      final PsiElement e = usage.getElement();
      if (e != null && ScopeUtil.getScopeOwner(e) == destination && getImportStatementByElement(e) == null) {
        PsiElement topLevel = PsiTreeUtil.findFirstParent(e, new Condition<PsiElement>() {
          @Override
          public boolean value(PsiElement element) {
            return element.getParent() == destination;
          }
        });
        if (topLevel != null) {
          topLevelAtDestination.add(topLevel);
        }
      }
    }
    if (topLevelAtDestination.isEmpty()) {
      return (PsiNamedElement)(destination.add(element));
    }
    else {
      Collections.sort(topLevelAtDestination, new Comparator<PsiElement>() {
        @Override
        public int compare(PsiElement e1, PsiElement e2) {
          return PsiUtilCore.compareElementsByPosition(e1, e2);
        };
      });
      final PsiElement firstUsage = topLevelAtDestination.get(0);
      return (PsiNamedElement)destination.addBefore(element, firstUsage);
    }
  }

  private static void updateUsage(@NotNull PsiElement usage, @NotNull PsiNamedElement oldElement, @NotNull PsiNamedElement newElement) {
    // TODO: Respect the qualified import style
    if (usage instanceof PyQualifiedExpression) {
      PyQualifiedExpression expr = (PyQualifiedExpression)usage;
      if (oldElement instanceof PyClass && PyNames.INIT.equals(expr.getName())) {
        return;
      }
      if (expr.getQualifier() != null) {
        final PyElementGenerator generator = PyElementGenerator.getInstance(expr.getProject());
        final PyExpression generated = generator.createExpressionFromText(LanguageLevel.forElement(expr), expr.getName());
        final PsiElement newExpr = expr.replace(generated);
        PyClassRefactoringUtil.insertImport(newExpr, newElement, null, true);
      }
    }
    if (usage instanceof PyStringLiteralExpression) {
      for (PsiReference ref : usage.getReferences()) {
        if (ref instanceof DocStringTypeReference && ref.isReferenceTo(oldElement)) {
          ref.bindToElement(newElement);
        }
      }
    }
    else {
      final PyImportStatementBase importStmt = getImportStatementByElement(usage);
      if (importStmt != null) {
        PyClassRefactoringUtil.updateImportOfElement(importStmt, newElement);
      }
      final PsiFile usageFile = usage.getContainingFile();
      if (usageFile == oldElement.getContainingFile() && !PsiTreeUtil.isAncestor(oldElement, usage, false)) {
        PyClassRefactoringUtil.insertImport(oldElement, newElement);
      }
      if (resolvesToLocalStarImport(usage)) {
        PyClassRefactoringUtil.insertImport(usage, newElement);
        if (usageFile != null) {
          optimizeImports(usageFile);
        }
      }
    }
  }

  private static void optimizeImports(@NotNull PsiFile file) {
    new PyImportOptimizer().processFile(file).run();
  }

  private static boolean resolvesToLocalStarImport(@NotNull PsiElement element) {
    final PsiReference ref = element.getReference();
    final List<PsiElement> resolvedElements = new ArrayList<PsiElement>();
    if (ref instanceof PsiPolyVariantReference) {
      for (ResolveResult result : ((PsiPolyVariantReference)ref).multiResolve(false)) {
        resolvedElements.add(result.getElement());
      }
    }
    else if (ref != null) {
      resolvedElements.add(ref.resolve());
    }
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      for (PsiElement resolved : resolvedElements) {
        if (resolved instanceof PyStarImportElement && resolved.getContainingFile() == containingFile) {
          return true;
        }
      }
    }
    return false;
  }

  private static void checkValidImportableFile(PsiElement anchor, VirtualFile file) {
    final QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(anchor, file);
    if (!PyClassRefactoringUtil.isValidQualifiedName(qName)) {
      throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.cannot.use.module.name.$0", qName));
    }
  }
}

