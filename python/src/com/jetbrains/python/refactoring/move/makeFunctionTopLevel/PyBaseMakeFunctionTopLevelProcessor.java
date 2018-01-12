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
package com.jetbrains.python.refactoring.move.makeFunctionTopLevel;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.refactoring.move.PyMoveRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseMakeFunctionTopLevelProcessor extends BaseRefactoringProcessor {
  protected final PyFunction myFunction;
  protected final PsiFile mySourceFile;
  protected final PyResolveContext myResolveContext;
  protected final PyElementGenerator myGenerator;
  protected final String myDestinationPath;
  protected final List<PsiElement> myExternalReads = new ArrayList<>();

  public PyBaseMakeFunctionTopLevelProcessor(@NotNull PyFunction targetFunction, @NotNull String destinationPath) {
    super(targetFunction.getProject());
    myFunction = targetFunction;
    myDestinationPath = destinationPath;
    final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(myProject, targetFunction.getContainingFile());
    myResolveContext = PyResolveContext.defaultContext().withTypeEvalContext(typeEvalContext);
    myGenerator = PyElementGenerator.getInstance(myProject);
    mySourceFile = myFunction.getContainingFile();
  }

  @NotNull
  @Override
  protected final UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
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
  protected final UsageInfo[] findUsages() {
    return ArrayUtil.toObjectArray(PyRefactoringUtil.findUsages(myFunction, false), UsageInfo.class);
  }

  @NotNull
  @Override
  protected final String getCommandName() {
    return getRefactoringName();
  }

  @Override
  protected final void performRefactoring(@NotNull UsageInfo[] usages) {
    final List<String> newParameters = collectNewParameterNames();

    assert ApplicationManager.getApplication().isWriteAccessAllowed();

    final PyFile targetFile = PyUtil.getOrCreateFile(myDestinationPath, myProject);
    if (targetFile.findTopLevelFunction(myFunction.getName()) != null) {
      throw new IncorrectOperationException(
        PyBundle.message("refactoring.move.error.destination.file.contains.function.$0", myFunction.getName()));
    }
    if (importsRequired(usages, targetFile)) {
      PyMoveRefactoringUtil.checkValidImportableFile(targetFile, targetFile.getVirtualFile());
    }

    final PsiElement position = PyMoveRefactoringUtil.findLowestPossibleTopLevelInsertionPosition(Arrays.asList(usages), targetFile);
    // We should update usages before we generate and insert new function, because we have to update its usages inside 
    // (e.g. recursive calls) it first 
    updateUsages(newParameters, usages);
    final PyFunction newFunction = insertFunction(createNewFunction(newParameters), targetFile, position);

    myFunction.delete();

    updateImports(newFunction, usages);
  }

  private boolean importsRequired(@NotNull UsageInfo[] usages, final PyFile targetFile) {
    return ContainerUtil.exists(usages, info -> {
      final PsiElement element = info.getElement();
      if (element == null) {
        return false;
      }
      return !belongsToFunction(element) && info.getFile() != targetFile;
    });
  }

  private boolean belongsToFunction(PsiElement element) {
    return PsiTreeUtil.isAncestor(myFunction, element, false);
  }


  private void updateImports(@NotNull PyFunction newFunction, @NotNull UsageInfo[] usages) {
    final Set<PsiFile> usageFiles = new HashSet<>();
    for (UsageInfo usage : usages) {
      usageFiles.add(usage.getFile());
    }
    for (PsiFile file : usageFiles) {
      if (file != newFunction.getContainingFile()) {
        PyClassRefactoringUtil.insertImport(file, newFunction, null, true);
      }
    }
    // References inside the body of function 
    if (newFunction.getContainingFile() != mySourceFile) {
      for (PsiElement read : myExternalReads) {
        if (read instanceof PsiNamedElement && read.isValid()) {
          PyClassRefactoringUtil.insertImport(newFunction, (PsiNamedElement)read, null, true);
        }
      }
      PyClassRefactoringUtil.optimizeImports(mySourceFile);
    }
  }

  @NotNull
  protected abstract String getRefactoringName();

  @NotNull
  protected abstract List<String> collectNewParameterNames();

  protected abstract void updateUsages(@NotNull Collection<String> newParamNames, @NotNull UsageInfo[] usages);
  
  @NotNull
  protected abstract PyFunction createNewFunction(@NotNull Collection<String> newParamNames);

  @NotNull
  protected final PyParameterList addParameters(@NotNull PyParameterList paramList, @NotNull Collection<String> newParameters) {
    if (!newParameters.isEmpty()) {
      final String commaSeparatedNames = StringUtil.join(newParameters, ", ");
      final StringBuilder paramListText = new StringBuilder(paramList.getText());
      paramListText.insert(1, commaSeparatedNames + (paramList.getParameters().length > 0 ? ", " : ""));
      final PyParameterList newElement = myGenerator.createParameterList(LanguageLevel.forElement(myFunction), paramListText.toString());
      return (PyParameterList)paramList.replace(newElement);
    }
    return paramList;
  }

  @NotNull
  protected PyArgumentList addArguments(@NotNull PyArgumentList argList, @NotNull Collection<String> newArguments) {
    if (!newArguments.isEmpty()) {
      final String commaSeparatedNames = StringUtil.join(newArguments, ", ");
      final StringBuilder argListText = new StringBuilder(argList.getText());
      argListText.insert(1, commaSeparatedNames + (argList.getArguments().length > 0 ? ", " : ""));
      final PyArgumentList newElement = myGenerator.createArgumentList(LanguageLevel.forElement(argList), argListText.toString());
      return (PyArgumentList)argList.replace(newElement);
    }
    return argList;
  }

  @NotNull
  protected PyFunction insertFunction(@NotNull PyFunction newFunction, @NotNull PyFile newFile, @Nullable PsiElement anchor) {
    if (mySourceFile == newFile) {
      // In the same file try inserting generated function at the top level but preferably right after the original scope owner
      final PsiElement surroundingStatement = PyPsiUtils.getParentRightBefore(myFunction, mySourceFile);
      if (anchor == null || surroundingStatement.getTextRange().getEndOffset() < anchor.getTextRange().getStartOffset()) {
        return (PyFunction)mySourceFile.addAfter(newFunction, surroundingStatement);
      }
    }
    // Insert at the end or before first top-level usage in the file
    return (PyFunction)newFile.addBefore(newFunction, anchor);
  }

  @NotNull
  protected AnalysisResult analyseScope(@NotNull ScopeOwner owner) {
    final ControlFlow controlFlow = ControlFlowCache.getControlFlow(owner);
    final AnalysisResult result = new AnalysisResult();
    for (Instruction instruction : controlFlow.getInstructions()) {
      if (instruction instanceof ReadWriteInstruction) {
        final ReadWriteInstruction readWriteInstruction = (ReadWriteInstruction)instruction;
        final PsiElement element = readWriteInstruction.getElement();
        if (element == null) {
          continue;
        }
        if (readWriteInstruction.getAccess().isReadAccess()) {
          for (PsiElement resolved : PyUtil.multiResolveTopPriority(element, myResolveContext)) {
            if (resolved != null) {
              if (isInitOrNewMethod(resolved)) {
                resolved = ((PyFunction)resolved).getContainingClass();
              }
              if (isFromEnclosingScope(resolved)) {
                result.readsFromEnclosingScope.add(element);
              }
              else if (!belongsToFunction(resolved)) {
                myExternalReads.add(resolved);
              }
              if (resolved instanceof PyParameter && ((PyParameter)resolved).isSelf()) {
                if (PsiTreeUtil.getParentOfType(resolved, PyFunction.class) == myFunction) {
                  result.readsOfSelfParameter.add(element);
                }
                else if (!PsiTreeUtil.isAncestor(myFunction, resolved, true)) {
                  result.readsOfSelfParametersFromEnclosingScope.add(element);
                }
              }
            }
          }
        }
        if (readWriteInstruction.getAccess().isWriteAccess() && element instanceof PyTargetExpression) {
          for (PsiElement resolved : PyUtil.multiResolveTopPriority(element, myResolveContext)) {
            if (resolved != null) {
              if (element.getParent() instanceof PyNonlocalStatement && isFromEnclosingScope(resolved)) {
                result.nonlocalWritesToEnclosingScope.add((PyTargetExpression)element);
              }
              if (resolved instanceof PyParameter && ((PyParameter)resolved).isSelf() && 
                  PsiTreeUtil.getParentOfType(resolved, PyFunction.class) == myFunction) {
                result.writesToSelfParameter.add((PyTargetExpression)element);
              }
            }
          }
        }
      }
    }
    return result;
  }

  private static boolean isInitOrNewMethod(@NotNull PsiElement elem) {
    final PyFunction func = as(elem, PyFunction.class);
    return func != null && (PyNames.INIT.equals(func.getName()) || PyNames.NEW.equals(func.getName()));
  }

  private boolean isFromEnclosingScope(@NotNull PsiElement element) {
    return PyUtil.inSameFile(element, myFunction) &&
           !belongsToFunction(element) &&
           !(ScopeUtil.getScopeOwner(element) instanceof PsiFile); 
  }

  protected static class AnalysisResult {
    final List<PsiElement> readsFromEnclosingScope = new ArrayList<>();
    final List<PyTargetExpression> nonlocalWritesToEnclosingScope = new ArrayList<>();
    final List<PsiElement> readsOfSelfParametersFromEnclosingScope = new ArrayList<>();
    final List<PsiElement> readsOfSelfParameter = new ArrayList<>();
    // No one writes to "self", but handle this case too just to be sure
    final List<PyTargetExpression> writesToSelfParameter = new ArrayList<>();
  }
}
