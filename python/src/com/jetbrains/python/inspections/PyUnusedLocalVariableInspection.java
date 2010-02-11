package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.Function;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.PyControlFlowUtil;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class PyUnusedLocalVariableInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @NotNull
  @Nls
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unused");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "PyUnusedLocalVariableInspection";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PyInspectionVisitor(holder) {
      @Override
      public void visitPyFile(final PyFile node) {
        processScope(node);
      }

      @Override
      public void visitPyClass(final PyClass node) {
        processScope(node);
      }

      @Override
      public void visitPyFunction(final PyFunction node) {
        processScope(node);
      }

      private void processScope(final ScopeOwner owner) {
        // TODO[oleg] Do not show warning in python code expression mode (evaluate in debug or watches)

        // If method overrides others do not mark parameters as unused if they are
        boolean parametersCanBeUnused = false;
        if (owner instanceof PyFunction) {
          parametersCanBeUnused = PySuperMethodsSearch.search(((PyFunction)owner)).findFirst() != null;
        }

        final HashMap<String, List<PsiElement>> unusedMap = new HashMap<String, List<PsiElement>>();

        final ControlFlow flow = owner.getControlFlow();
        final Scope scope = owner.getScope();
        final Instruction[] instructions = flow.getInstructions();

        // Iteration over write accesses
        for (int i = 0; i < instructions.length; i++) {
          final Instruction instruction = instructions[i];
          if (instruction instanceof ReadWriteInstruction) {
            final String name = ((ReadWriteInstruction)instruction).getName();
            final PsiElement element = instruction.getElement();
            final ReadWriteInstruction.ACCESS access = ((ReadWriteInstruction)instruction).getAccess();
            // WriteAccess
            if (access.isWriteAccess() &&
                (parametersCanBeUnused || !(element != null && element.getParent() instanceof PyNamedParameter))) {
              addToUnused(unusedMap, name, element);
            }
          }
        }

        // Iteration over read accesses
        for (int i = 0; i < instructions.length; i++) {
          final Instruction instruction = instructions[i];
          if (instruction instanceof ReadWriteInstruction) {
            final String name = ((ReadWriteInstruction)instruction).getName();
            final PsiElement element = instruction.getElement();
            final ReadWriteInstruction.ACCESS access = ((ReadWriteInstruction)instruction).getAccess();
            // Read or self assign access
            if (access.isReadAccess()) {
              int number = i;
              if (access == ReadWriteInstruction.ACCESS.READWRITE) {
                final PyAugAssignmentStatement augAssignmentStatement = PyAugAssignmentStatementNavigator.getStatementByTarget(element);
                number = ControlFlowUtil.findInstructionNumberByElement(instructions, augAssignmentStatement);
              }

              PyControlFlowUtil.iterateWriteAccessFor(name, number, instructions, new Function<ReadWriteInstruction, PyControlFlowUtil.Operation>() {
                public PyControlFlowUtil.Operation fun(final ReadWriteInstruction rwInstr) {
                  final PsiElement instrElement = rwInstr.getElement();
                    removeFromUnused(unusedMap, name, instrElement);
                    return PyControlFlowUtil.Operation.CONTINUE;
                }
              });
            }
          }
        }
        // Register problems
        for (List<PsiElement> list : unusedMap.values()) {
          for (PsiElement element : list) {
            final String name = element.getText();
            if (element instanceof PyNamedParameter) {
              registerWarning(element, PyBundle.message("INSP.unused.locals.parameter.isnot.used", name));
            }
            else {
              registerWarning(element, PyBundle.message("INSP.unused.locals.local.variable.isnot.used", name));
            }
          }
        }
      }

      private void registerWarning(final PsiElement element, final String msg) {
        registerProblem(element, msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL, null);
      }

      private void removeFromUnused(final HashMap<String, List<PsiElement>> unusedMap, final String name, final PsiElement element) {
        final List<PsiElement> list = unusedMap.get(name);
        if (list == null) {
          return;
        }
        list.remove(element);
      }

      private void addToUnused(final HashMap<String, List<PsiElement>> unusedMap, final String name, final PsiElement element) {
        List<PsiElement> list = unusedMap.get(name);
        if (list == null) {
          list = new ArrayList<PsiElement>();
          unusedMap.put(name, list);
        }
        list.add(element);
      }
    };
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }
}
