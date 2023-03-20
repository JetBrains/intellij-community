/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.psi.impl.PyTypeDeclarationStatementNavigator.isTypeDeclarationTarget;

/**
 * Annotates declarations that unconditionally override others without these being used.
 */
public class PyRedeclarationInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }
    @Override
    public void visitPyFunction(final @NotNull PyFunction node) {
      if (!PyKnownDecoratorUtil.hasUnknownDecorator(node, myTypeEvalContext) &&
          !PyKnownDecoratorUtil.hasRedeclarationDecorator(node, myTypeEvalContext)) {
        processElement(node);
      }
    }

    @Override
    public void visitPyTargetExpression(final @NotNull PyTargetExpression node) {
      if (node.isQualified() || PyNames.UNDERSCORE.equals(node.getText()) || isTypeDeclarationTarget(node)) return;
      final ScopeOwner owner = ScopeUtil.getScopeOwner(node);
      if (owner instanceof PyFile || owner instanceof PyClass) {
        processElement(node);
      }
    }

    @Override
    public void visitPyClass(final @NotNull PyClass node) {
      if (!isDecorated(node)) {
        processElement(node);
      }
    }

    private static boolean isDecorated(@NotNull PyDecoratable node) {
      boolean isDecorated = false;
      final PyDecoratorList decoratorList = node.getDecoratorList();
      if (decoratorList != null) {
        final PyDecorator[] decorators = decoratorList.getDecorators();
        if (decorators.length > 0) {
          isDecorated = true;
        }
      }
      return isDecorated;
    }

    private void processElement(@NotNull final PsiNameIdentifierOwner element) {
      final String name = element.getName();
      final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
      if (owner != null && name != null) {
        final Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
        PsiElement elementInControlFlow = element;
        if (element instanceof PyTargetExpression) {
          final PyImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class);
          if (importStatement != null) {
            elementInControlFlow = importStatement;
          }
        }
        final int startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, elementInControlFlow);
        if (startInstruction < 0) {
          return;
        }
        final Ref<PsiElement> readElementRef = Ref.create(null);
        final Ref<PsiElement> writeElementRef = Ref.create(null);
        final Ref<Boolean> underPossiblyFalseCondition = Ref.create(false);
        ControlFlowUtil.iteratePrev(startInstruction, instructions, instruction -> {
          if (instruction instanceof ReadWriteInstruction rwInstruction && instruction.num() != startInstruction) {
            if (name.equals(rwInstruction.getName())) {
              final PsiElement originalElement = rwInstruction.getElement();
              if (originalElement != null) {
                if (rwInstruction.getAccess().isReadAccess()) {
                  readElementRef.set(originalElement);
                }
                if (rwInstruction.getAccess().isWriteAccess() && originalElement != element) {
                  if (PyiUtil.isOverload(originalElement, myTypeEvalContext) || isTypeDeclarationTarget(originalElement)) {
                    return ControlFlowUtil.Operation.NEXT;
                  }
                  else if (!underPossiblyFalseCondition.get()) {
                    writeElementRef.set(originalElement);
                  }
                }
              }
              return ControlFlowUtil.Operation.CONTINUE;
            }
          }
          if (possiblyFalseCondition(instruction)) {
            underPossiblyFalseCondition.set(true);
          }
          return ControlFlowUtil.Operation.NEXT;
        });
        final PsiElement writeElement = writeElementRef.get();
        if (writeElement != null && readElementRef.get() == null) {
          // Repeated patterns are reported as syntactic errors in an annotator
          if (PsiTreeUtil.findCommonParent(writeElement, element) instanceof PyPattern) {
            return;
          }
          final List<LocalQuickFix> quickFixes = new ArrayList<>();
          if (suggestRename(element, writeElement)) {
            LocalQuickFix quickFix = PythonUiService.getInstance().createPyRenameElementQuickFix(element);
            if (quickFix != null) {
              quickFixes.add(quickFix);
            }
          }
          final PsiElement identifier = element.getNameIdentifier();
          registerProblem(identifier != null ? identifier : element,
                          PyPsiBundle.message("INSP.redeclared.name", name),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                          null,
                          quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
      }
    }

    private static boolean possiblyFalseCondition(@NotNull Instruction instruction) {
      final PsiElement element = instruction.getElement();
      if (element == null) return false;

      if (element instanceof PyTryPart || element instanceof PyExceptPart || element instanceof PyConditionalExpression) return true;

      if (element instanceof PyForStatement) {
        final PyForPart forPart = ((PyForStatement)element).getForPart();
        return !PyEvaluator.evaluateAsBoolean(forPart.getSource(), false);
      }

      if (instruction instanceof ConditionalInstruction conditionalInstruction) {
        final PsiElement condition = conditionalInstruction.getCondition();
        if (condition instanceof PyExpression) {
          return conditionalInstruction.getResult()
                 ? !PyEvaluator.evaluateAsBoolean((PyExpression)condition, false)
                 : PyEvaluator.evaluateAsBoolean((PyExpression)condition, true);
        }
      }

      return false;
    }

    private static boolean suggestRename(@NotNull PsiNameIdentifierOwner element, @NotNull PsiElement originalElement) {
      // Target expressions in the same scope are treated as the same variable
      if ((element instanceof PyTargetExpression) && originalElement instanceof PyTargetExpression) {
        return false;
      }
      // Renaming an __init__ method results in renaming its class
      else if (PyUtil.isInitMethod(element)) {
        return false;
      }
      return true;
    }
  }
}
