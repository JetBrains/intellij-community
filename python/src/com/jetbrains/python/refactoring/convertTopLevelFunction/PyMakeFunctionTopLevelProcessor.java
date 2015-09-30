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

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
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
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyMakeFunctionTopLevelProcessor extends BaseRefactoringProcessor {
  private final PyFunction myFunction;
  private final PyResolveContext myContext;
  private final Editor myEditor;

  protected PyMakeFunctionTopLevelProcessor(@NotNull PyFunction targetFunction, @NotNull Editor editor) {
    super(targetFunction.getProject());
    myFunction = targetFunction;
    myEditor = editor;
    final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(myProject, targetFunction.getContainingFile());
    myContext = PyResolveContext.defaultContext().withTypeEvalContext(typeEvalContext);
    setPreviewUsages(isForMethod());
  }

  private boolean isForMethod() {
    return myFunction.getContainingClass() != null;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[] {myFunction};
      }

      @Override
      public String getProcessedElementsHeader() {
        return getRefactoringName();
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    return ArrayUtil.toObjectArray(PyRefactoringUtil.findUsages(myFunction, false), UsageInfo.class);
  }

  @Override
  protected String getCommandName() {
    return getRefactoringName();
  }

  @NotNull
  private String getRefactoringName() {
    return isForMethod() ? PyBundle.message("refactoring.make.method.top.level")
                         : PyBundle.message("refactoring.make.local.function.top.level");
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    if (isForMethod()) {
      // TODO escalate method
    }
    else {
      escalateLocalFunction(usages);
    }

  }

  private void escalateLocalFunction(@NotNull UsageInfo[] usages) {
    final Set<String> enclosingScopeReads = new LinkedHashSet<String>();
    final Collection<ScopeOwner> scopeOwners = PsiTreeUtil.collectElementsOfType(myFunction, ScopeOwner.class);
    for (ScopeOwner owner : scopeOwners) {
      final PyMakeFunctionTopLevelProcessor.AnalysisResult scope = findReadsFromEnclosingScope(owner);
      if (!scope.nonlocalWritesToEnclosingScope.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.method.top.level.error.nonlocal.writes"));
      }
      for (PsiElement element : scope.readFromEnclosingScope) {
        if (element instanceof PyElement) {
          ContainerUtil.addIfNotNull(enclosingScopeReads, ((PyElement)element).getName());
        }
      }
    }

    assert ApplicationManager.getApplication().isWriteAccessAllowed();
    updateLocalFunctionAndUsages(myEditor, enclosingScopeReads, usages);
  }

  private void updateLocalFunctionAndUsages(@NotNull Editor editor, @NotNull Set<String> enclosingScopeReads, UsageInfo[] usages) {
    final String commaSeparatedNames = StringUtil.join(enclosingScopeReads, ", ");
    final Project project = myFunction.getProject();

    // Update existing usages
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    for (UsageInfo usage : usages) {
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
    PyFunction copiedFunction = (PyFunction)myFunction.copy();
    final PyParameterList paramList = copiedFunction.getParameterList();
    final StringBuilder paramListText = new StringBuilder(paramList.getText());
    paramListText.insert(1, commaSeparatedNames + (paramList.getParameters().length > 0 ? ", " : ""));
    paramList.replace(elementGenerator.createParameterList(LanguageLevel.forElement(myFunction), paramListText.toString()));

    // See AddImportHelper.getFileInsertPosition()
    final PsiFile file = myFunction.getContainingFile();
    final PsiElement anchor = PyPsiUtils.getParentRightBefore(myFunction, file);

    copiedFunction = (PyFunction)file.addAfter(copiedFunction, anchor);
    myFunction.delete();

    editor.getSelectionModel().removeSelection();
    editor.getCaretModel().moveToOffset(copiedFunction.getTextOffset());
  }

  @NotNull
  private AnalysisResult findReadsFromEnclosingScope(@NotNull ScopeOwner owner) {
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
          for (PsiElement resolved : PyUtil.multiResolveTopPriority(element, myContext)) {
            if (resolved != null && isFromEnclosingScope(resolved)) {
              readFromEnclosingScope.add(element);
              break;
            }
          }
        }
        if (readWriteInstruction.getAccess().isWriteAccess()) {
          if (element instanceof PyTargetExpression && element.getParent() instanceof PyNonlocalStatement) {
            for (PsiElement resolved : PyUtil.multiResolveTopPriority(element, myContext)) {
              if (resolved != null && isFromEnclosingScope(resolved)) {
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

  private boolean isFromEnclosingScope(@NotNull PsiElement element) {
    return !PsiTreeUtil.isAncestor(myFunction, element, false) && !(ScopeUtil.getScopeOwner(element) instanceof PsiFile);
  }

  static class AnalysisResult {
    final List<PsiElement> readFromEnclosingScope;
    final List<PyTargetExpression> nonlocalWritesToEnclosingScope;

    public AnalysisResult(@NotNull List<PsiElement> readFromEnclosingScope, @NotNull List<PyTargetExpression> nonlocalWrites) {
      this.readFromEnclosingScope = readFromEnclosingScope;
      this.nonlocalWritesToEnclosingScope = nonlocalWrites;
    }
  }
}
