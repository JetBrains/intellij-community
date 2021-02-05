package de.plushnikov.intellij.plugin.inspection.modifiers;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.fixes.RemoveModifierFix;
import de.plushnikov.intellij.plugin.inspection.LombokJavaInspectionBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

public abstract class LombokRedundantModifierInspection extends LombokJavaInspectionBase {

  private final String supportedAnnotation;
  private final RedundantModifiersInfo[] redundantModifiersInfo;

  public LombokRedundantModifierInspection(@Nullable String supportedAnnotation, RedundantModifiersInfo... redundantModifiersInfo) {
    this.supportedAnnotation = supportedAnnotation;
    this.redundantModifiersInfo = redundantModifiersInfo;
  }

  @NotNull
  @Override
  protected PsiElementVisitor createVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new LombokRedundantModifiersVisitor(holder);
  }

  private class LombokRedundantModifiersVisitor extends JavaElementVisitor {
    private final ProblemsHolder holder;

    LombokRedundantModifiersVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);

      this.visit(aClass);
    }

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);

      this.visit(field);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);

      this.visit(method);
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      super.visitLocalVariable(variable);

      this.visit(variable);
    }

    @Override
    public void visitParameter(PsiParameter parameter) {
      super.visitParameter(parameter);

      this.visit(parameter);
    }

    private void visit(PsiModifierListOwner psiModifierListOwner) {
      for (RedundantModifiersInfo redundantModifiersInfo : redundantModifiersInfo) {
        RedundantModifiersInfoType infoType = redundantModifiersInfo.getType();
        PsiModifierListOwner parentModifierListOwner = PsiTreeUtil.getParentOfType(psiModifierListOwner,
          PsiModifierListOwner.class, infoType != RedundantModifiersInfoType.CLASS && infoType != RedundantModifiersInfoType.VARIABLE);
        if (parentModifierListOwner == null) {
          continue;
        }
        if (infoType == RedundantModifiersInfoType.VARIABLE && !(parentModifierListOwner instanceof PsiLocalVariable || parentModifierListOwner instanceof PsiParameter)
          || (infoType != RedundantModifiersInfoType.VARIABLE && !(parentModifierListOwner instanceof PsiClass))) {
          continue;
        }
        if ((supportedAnnotation == null || parentModifierListOwner.hasAnnotation(supportedAnnotation)) &&
          redundantModifiersInfo.getType().getSupportedClass().isAssignableFrom(psiModifierListOwner.getClass())) {
          PsiModifierList psiModifierList = psiModifierListOwner.getModifierList();
          if (psiModifierList == null ||
            (redundantModifiersInfo.getDontRunOnModifier() != null && psiModifierList.hasExplicitModifier(redundantModifiersInfo.getDontRunOnModifier()))) {
            continue;
          }
          if (!redundantModifiersInfo.shouldCheck(psiModifierListOwner)) {
            continue;
          }
          for (String modifier : redundantModifiersInfo.getModifiers()) {
            if (psiModifierList.hasExplicitModifier(modifier)) {
              final Optional<PsiElement> psiModifier = Arrays.stream(psiModifierList.getChildren())
                .filter(psiElement -> modifier.equals(psiElement.getText()))
                .findFirst();

              psiModifier.ifPresent(psiElement -> holder.registerProblem(psiElement,
                redundantModifiersInfo.getDescription(),
                ProblemHighlightType.WARNING,
                new RemoveModifierFix(modifier))
              );
            }
          }
        }
      }
    }
  }
}
