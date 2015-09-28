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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
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
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyConvertLocalFunctionToTopLevelFunction extends BaseIntentionAction {
  public PyConvertLocalFunctionToTopLevelFunction() {
    setText(PyBundle.message("INTN.convert.local.function.to.top.level.function"));
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.local.function.to.top.level.function");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PyFunction nestedFunction = findNestedFunctionUnderCaret(editor, file);
    return nestedFunction != null;
  }

  @Nullable
  private static PyFunction findNestedFunctionUnderCaret(Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) return null;
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    if (element == null) {
      return null;
    }
    PyFunction result = null;
    if (isLocalFunction(element.getParent()) && ((PyFunction)element.getParent()).getNameIdentifier() == element) {
      result = (PyFunction)element.getParent();
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
    if (result != null) {
      final VirtualFile virtualFile = result.getContainingFile().getVirtualFile();
      if (virtualFile != null && ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
        return null;
      }
    }
    return result;
  }

  private static boolean isLocalFunction(@Nullable PsiElement resolved) {
    if (resolved instanceof PyFunction && PsiTreeUtil.getParentOfType(resolved, ScopeOwner.class, true) instanceof PyFunction) {
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PyFunction function = findNestedFunctionUnderCaret(editor, file);
    assert function != null;
    final Set<String> enclosingScopeReads = new LinkedHashSet<String>(); 
    final Collection<ScopeOwner> scopeOwners = PsiTreeUtil.collectElementsOfType(function, ScopeOwner.class);
    for (ScopeOwner owner : scopeOwners) {
      for (PsiElement element : findReadsFromEnclosingScope(owner, function)) {
        if (element instanceof PyElement) {
          ContainerUtil.addIfNotNull(enclosingScopeReads, ((PyElement)element).getName());
        }
      }
    }
    final String commaSeparatedNames = StringUtil.join(enclosingScopeReads, ", ");

    // Update existing usages
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
    for (UsageInfo usage : PyRefactoringUtil.findUsages(function, false)) {
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
    PyFunction copiedFunction = (PyFunction)function.copy();
    final PyParameterList paramList = copiedFunction.getParameterList();
    final StringBuilder paramListText = new StringBuilder(paramList.getText());
    paramListText.insert(1, commaSeparatedNames + (paramList.getParameters().length > 0 ? ", " : ""));
    paramList.replace(elementGenerator.createParameterList(LanguageLevel.forElement(function), paramListText.toString()));

    // See AddImportHelper.getFileInsertPosition()
    final PsiElement anchor = PyPsiUtils.getParentRightBefore(function, file);

    copiedFunction = (PyFunction)file.addAfter(copiedFunction, anchor);
    function.delete();
    
    editor.getSelectionModel().removeSelection();
    editor.getCaretModel().moveToOffset(copiedFunction.getTextOffset());
  }

  @NotNull
  private static List<PsiElement> findReadsFromEnclosingScope(@NotNull ScopeOwner owner, @NotNull PyFunction targetFunction) {
    final ControlFlow controlFlow = ControlFlowCache.getControlFlow(owner);
    final List<PsiElement> result = new ArrayList<PsiElement>();
    for (Instruction instruction : controlFlow.getInstructions()) {
      if (instruction instanceof ReadWriteInstruction) {
        final ReadWriteInstruction readInstruction = (ReadWriteInstruction)instruction;
        if (readInstruction.getAccess().isReadAccess()) {
          final PsiElement element = readInstruction.getElement();
          if (element != null) {
            for (PsiReference reference : element.getReferences()) {
              final PsiElement resolved = reference.resolve();
              if (resolved != null && isFromEnclosingScope(resolved, targetFunction)) {
                result.add(element);
                break;
              }
            }
          }
        }
      }
    }
    return result; 
  }

  private static boolean isFromEnclosingScope(@NotNull PsiElement element, @NotNull PyFunction targetFunction) {
    return !PsiTreeUtil.isAncestor(targetFunction, element, false) && !(ScopeUtil.getScopeOwner(element) instanceof PsiFile);
  }
}
