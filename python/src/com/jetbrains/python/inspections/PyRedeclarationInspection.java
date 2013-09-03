package com.jetbrains.python.inspections;

import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.Function;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Annotates declarations that unconditionally override others without these being used.
 *
 * @author dcheryasov
 * @author vlan
 *
 * TODO: Add a rename quick-fix
 */
public class PyRedeclarationInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.redeclaration");
  }


  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      if (!isDecorated(node)) {
        processElement(node);
      }
    }

    @Override
    public void visitPyTargetExpression(final PyTargetExpression node) {
      final ScopeOwner owner = ScopeUtil.getScopeOwner(node);
      if (owner instanceof PyFile || owner instanceof PyClass) {
        processElement(node);
      }
    }

    @Override
    public void visitPyClass(final PyClass node) {
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
        final int startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, element);
        ControlFlowUtil.iteratePrev(startInstruction, instructions, new Function<Instruction, ControlFlowUtil.Operation>() {
          @Override
          public ControlFlowUtil.Operation fun(Instruction instruction) {
            if (instruction instanceof ReadWriteInstruction && instruction.num() != startInstruction) {
              final ReadWriteInstruction rwInstruction = (ReadWriteInstruction)instruction;
              if (name.equals(rwInstruction.getName())) {
                if (rwInstruction.getAccess().isWriteAccess()) {
                  final PsiElement shadowed = rwInstruction.getElement();
                  final PsiElement identifier = element.getNameIdentifier();
                  registerProblem(identifier != null ? identifier : element,
                                  PyBundle.message("INSP.shadows.same.named.$0.above", getKind(shadowed)));
                }
                return ControlFlowUtil.Operation.BREAK;
              }
            }
            return ControlFlowUtil.Operation.NEXT;
          }
        });
      }
    }
  }

  @NotNull
  private static String getKind(@Nullable PsiElement element) {
    if (element instanceof PyFunction) {
      return PyBundle.message("GNAME.function");
    }
    else if (element instanceof PyClass) {
      return PyBundle.message("GNAME.class");
    }
    else {
      return PyBundle.message("GNAME.var");
    }
  }
}
