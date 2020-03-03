package de.plushnikov.intellij.plugin.inspection.modifiers;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

import static de.plushnikov.intellij.plugin.inspection.modifiers.RedundantModifiersInfoType.*;

public abstract class LombokRedundantModifierInspection extends AbstractBaseJavaLocalInspectionTool {

  private final Class<?> supportedAnnotation;
  private final RedundantModifiersInfo[] redundantModifiersInfo;

  public LombokRedundantModifierInspection(Class<?> supportedAnnotation, RedundantModifiersInfo... redundantModifiersInfo) {
    this.supportedAnnotation = supportedAnnotation;
    this.redundantModifiersInfo = redundantModifiersInfo;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
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

    private void visit(PsiModifierListOwner psiModifierListOwner) {
      for (RedundantModifiersInfo redundantModifiersInfo : redundantModifiersInfo) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(psiModifierListOwner, PsiClass.class, redundantModifiersInfo.getRedundantModifiersInfoType() == INNER_CLASS);
        if (containingClass == null) {
          continue;
        }
        if (containingClass.hasAnnotation(supportedAnnotation.getName()) &&
          redundantModifiersInfo.getRedundantModifiersInfoType().getSupportedClass().isAssignableFrom(psiModifierListOwner.getClass())) {
          PsiModifierList psiModifierList = psiModifierListOwner.getModifierList();
          if (psiModifierList == null ||
            (redundantModifiersInfo.getDontRunOnModifier() != null && psiModifierList.hasExplicitModifier(redundantModifiersInfo.getDontRunOnModifier()))) {
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
