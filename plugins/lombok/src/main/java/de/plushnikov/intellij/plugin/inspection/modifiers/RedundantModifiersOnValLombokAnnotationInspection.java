package de.plushnikov.intellij.plugin.inspection.modifiers;

import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.processor.ValProcessor;

import static com.intellij.psi.PsiModifier.FINAL;
import static de.plushnikov.intellij.plugin.inspection.modifiers.RedundantModifiersInfoType.VARIABLE;

public class RedundantModifiersOnValLombokAnnotationInspection extends LombokRedundantModifierInspection {

  public RedundantModifiersOnValLombokAnnotationInspection() {
    super(null, new RedundantModifiersInfo(VARIABLE, null, LombokBundle.message("inspection.message.val.already.marks.variables.final"), FINAL) {
      @Override
      public boolean shouldCheck(PsiModifierListOwner psiModifierListOwner) {
        PsiVariable psiVariable = (PsiVariable) psiModifierListOwner;
        return ValProcessor.isVal(psiVariable);
      }
    });
  }
}
