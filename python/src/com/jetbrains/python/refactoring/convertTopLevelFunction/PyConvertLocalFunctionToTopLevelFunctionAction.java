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
package com.jetbrains.python.refactoring.convertTopLevelFunction;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyBaseRefactoringAction;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyConvertLocalFunctionToTopLevelFunctionAction extends PyBaseRefactoringAction {
  public static final String ID = "py.convert.local.function.to.top.level.function";

  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isEnabledOnElementInsideEditor(@NotNull PsiElement element,
                                                   @NotNull Editor editor,
                                                   @NotNull PsiFile file,
                                                   @NotNull DataContext context) {
    return findNestedFunction(element) != null;
  }

  @Override
  protected boolean isEnabledOnElementsOutsideEditor(@NotNull PsiElement[] elements) {
    return false;
  }

  @Nullable
  private static PyFunction findNestedFunction(@NotNull PsiElement element) {
    PyFunction result = null;
    if (isLocalFunction(element)) {
      result = (PyFunction)element;
    }
    else {
      final PyReferenceExpression refExpr = PsiTreeUtil.getParentOfType(element, PyReferenceExpression.class);
      if (refExpr == null) {
        return null;
      }
      final PsiElement resolved = refExpr.getReference().resolve();
      if (isLocalFunction(resolved)) {
        result = (PyFunction)resolved;
      }
    }
    //if (result != null) {
    //  final VirtualFile virtualFile = result.getContainingFile().getVirtualFile();
    //  if (virtualFile != null && ProjectRootManager.getInstance(element.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
    //    return null;
    //  }
    //}
    return result;
  }

  @Nullable
  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new RefactoringActionHandler() {
      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
        if (element != null) {
          escalateFunction(project, file, editor, element);
        }
      }

      @Override
      public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (editor != null && elements.length == 1) {
          escalateFunction(project, elements[0].getContainingFile(), editor, elements[0]);
        }
      }
    };
  }

  private static boolean isLocalFunction(@Nullable PsiElement resolved) {
    if (resolved instanceof PyFunction && PsiTreeUtil.getParentOfType(resolved, ScopeOwner.class, true) instanceof PyFunction) {
      return true;
    }
    return false;
  }

  @VisibleForTesting
  public void escalateFunction(@NotNull Project project,
                               @NotNull PsiFile file,
                               @NotNull final Editor editor,
                               @NotNull PsiElement targetElement) throws IncorrectOperationException {
    final PyResolveContext context = PyResolveContext.defaultContext().withTypeEvalContext(TypeEvalContext.userInitiated(project, file));
    final PyFunction function = findNestedFunction(targetElement);
    assert function != null;
    final Set<String> enclosingScopeReads = new LinkedHashSet<String>();
    final Collection<ScopeOwner> scopeOwners = PsiTreeUtil.collectElementsOfType(function, ScopeOwner.class);
    for (ScopeOwner owner : scopeOwners) {
      final AnalysisResult scope = findReadsFromEnclosingScope(owner, function, context);
      if (!scope.nonlocalWritesToEnclosingScope.isEmpty()) {
        final String errMsg = PyBundle.message("INTN.convert.local.function.to.top.level.function.nonlocal");
        CommonRefactoringUtil.showErrorHint(project, editor, errMsg, null, ID);
        return;
      }
      for (PsiElement element : scope.readFromEnclosingScope) {
        if (element instanceof PyElement) {
          ContainerUtil.addIfNotNull(enclosingScopeReads, ((PyElement)element).getName());
        }
      }
    }

    WriteCommandAction.runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        updateUsagesAndFunction(editor, function, enclosingScopeReads);
      }
    });
  }

  private static void updateUsagesAndFunction(@NotNull Editor editor, 
                                              @NotNull PyFunction targetFunction,
                                              @NotNull Set<String> enclosingScopeReads) {
    final String commaSeparatedNames = StringUtil.join(enclosingScopeReads, ", ");
    final Project project = targetFunction.getProject();

    // Update existing usages
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    for (UsageInfo usage : PyRefactoringUtil.findUsages(targetFunction, false)) {
      final PsiElement element = usage.getElement();
      if (element != null) {
        final PyCallExpression parentCall = as(element.getParent(), PyCallExpression.class);
        if (parentCall != null) {
          final PyArgumentList argList = parentCall.getArgumentList();
          if (argList != null) {
            final StringBuilder argListText = new StringBuilder(argList.getText());
            argListText.insert(1, commaSeparatedNames + (argList.getArguments().length > 0 ? ", " : ""));
            argList.replace(elementGenerator.createArgumentList(LanguageLevel.forElement(element), argListText.toString()));
          }
        }
      }
    }

    // Replace function
    PyFunction copiedFunction = (PyFunction)targetFunction.copy();
    final PyParameterList paramList = copiedFunction.getParameterList();
    final StringBuilder paramListText = new StringBuilder(paramList.getText());
    paramListText.insert(1, commaSeparatedNames + (paramList.getParameters().length > 0 ? ", " : ""));
    paramList.replace(elementGenerator.createParameterList(LanguageLevel.forElement(targetFunction), paramListText.toString()));

    // See AddImportHelper.getFileInsertPosition()
    final PsiFile file = targetFunction.getContainingFile();
    final PsiElement anchor = PyPsiUtils.getParentRightBefore(targetFunction, file);

    copiedFunction = (PyFunction)file.addAfter(copiedFunction, anchor);
    targetFunction.delete();

    editor.getSelectionModel().removeSelection();
    editor.getCaretModel().moveToOffset(copiedFunction.getTextOffset());
  }

  @NotNull
  private static AnalysisResult findReadsFromEnclosingScope(@NotNull ScopeOwner owner,
                                                            @NotNull PyFunction targetFunction,
                                                            @NotNull PyResolveContext context) {
    final ControlFlow controlFlow = ControlFlowCache.getControlFlow(owner);
    final List<PsiElement> readFromEnclosingScope = new ArrayList<PsiElement>();
    final List<PyTargetExpression> nonlocalWrites = new ArrayList<PyTargetExpression>(); 
    for (Instruction instruction : controlFlow.getInstructions()) {
      if (instruction instanceof ReadWriteInstruction) {
        final ReadWriteInstruction readWriteInstruction = (ReadWriteInstruction)instruction;
        final PsiElement element = readWriteInstruction.getElement();
        if (element == null) {
          continue;
        }
        if (readWriteInstruction.getAccess().isReadAccess()) {
          for (PsiElement resolved : PyUtil.multiResolveTopPriority(element, context)) {
            if (resolved != null && isFromEnclosingScope(resolved, targetFunction)) {
              readFromEnclosingScope.add(element);
              break;
            }
          }
        }
        if (readWriteInstruction.getAccess().isWriteAccess()) {
          if (element instanceof PyTargetExpression && element.getParent() instanceof PyNonlocalStatement) {
            for (PsiElement resolved : PyUtil.multiResolveTopPriority(element, context)) {
              if (resolved != null && isFromEnclosingScope(resolved, targetFunction)) {
                nonlocalWrites.add((PyTargetExpression)element);
                break;
              }
            }
          }
        }
      }
    }
    return new AnalysisResult(readFromEnclosingScope, nonlocalWrites); 
  }

  private static class AnalysisResult {
    final List<PsiElement> readFromEnclosingScope;
    final List<PyTargetExpression> nonlocalWritesToEnclosingScope;

    public AnalysisResult(@NotNull List<PsiElement> readFromEnclosingScope, @NotNull List<PyTargetExpression> nonlocalWrites) {
      this.readFromEnclosingScope = readFromEnclosingScope;
      this.nonlocalWritesToEnclosingScope = nonlocalWrites;
    }
  }

  private static boolean isFromEnclosingScope(@NotNull PsiElement element, @NotNull PyFunction targetFunction) {
    return !PsiTreeUtil.isAncestor(targetFunction, element, false) && !(ScopeUtil.getScopeOwner(element) instanceof PsiFile);
  }
}
