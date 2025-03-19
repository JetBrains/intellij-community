package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.util.ImportsUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExpandStaticImportFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.expand.static.import");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PsiImportStaticReferenceElement referenceElement) {
      PsiImportStaticStatement staticImport = getImportStaticStatement(referenceElement);
      List<PsiJavaCodeReferenceElement> expressionToExpand =
        ImportsUtil.collectReferencesThrough(referenceElement.getContainingFile(), referenceElement, staticImport);
      ImportsUtil.replaceAllAndDeleteImport(expressionToExpand, referenceElement, staticImport);
    }
  }

  private static PsiImportStaticStatement getImportStaticStatement(PsiJavaCodeReferenceElement referenceElement) {
    PsiElement parent = referenceElement.getParent();
    return parent instanceof PsiImportStaticStatement importStatic ? importStatic :
           ObjectUtils.tryCast(referenceElement.advancedResolve(true).getCurrentFileResolveScope(), PsiImportStaticStatement.class);
  }
}