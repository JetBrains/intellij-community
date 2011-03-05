package com.jetbrains.python.inspections;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyExceptPart;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Detects unreachable code using control flow graph
 */
public class PyUnreachableCodeInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unreachable.code");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitElement(final PsiElement element) {
      if (element instanceof ScopeOwner) {
        // Look for decoupled components of control flow graph
        final Instruction[] flow = ControlFlowCache.getControlFlow((ScopeOwner)element).getInstructions();
        final int[] colors = new int[flow.length];
        for (int i = 0;i<flow.length;i++){
          colors[i] = i;
        }

        boolean colorChanged;
        do {
          colorChanged = false;
          for (Instruction instruction : flow) {
            for (Instruction succ : instruction.allSucc()) {
              if (colors[instruction.num()] < colors[succ.num()]){
                colors[succ.num()] = colors[instruction.num()];
                colorChanged = true;
              }
            }
          }
        } while (colorChanged);

        int color = colors[0];
        final boolean[] warned = new boolean[flow.length];
        Arrays.fill(warned, false);
        for (Instruction instruction : flow) {
          final PsiElement e = instruction.getElement();
          if (colors[instruction.num()] != color){
            color = colors[instruction.num()];
            if (color != 0 && !warned[color]){
              warned[color] = true;
              // Handle ensure parts
              if (e instanceof PyExceptPart) {
                continue;
              }
              registerProblem(e, PyBundle.message("INSP.unreachable.code"));
            }
          }
        }
      }
    }
  }
}
