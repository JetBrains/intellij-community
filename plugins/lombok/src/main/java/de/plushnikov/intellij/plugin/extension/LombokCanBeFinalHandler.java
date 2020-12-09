package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInspection.canBeFinal.CanBeFinalHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;

/**
 * Handler to produce a veto for elements with lombok methods behind
 */
public class LombokCanBeFinalHandler extends CanBeFinalHandler {

  @Override
  public boolean canBeFinal(PsiMember member) {
    if (member instanceof PsiField) {
      if (PsiAnnotationSearchUtil.isAnnotatedWith(member, LombokClassNames.SETTER)) {
        return false;
      }

      final PsiClass psiClass = PsiTreeUtil.getParentOfType(member, PsiClass.class);
      return null == psiClass || !PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.SETTER, LombokClassNames.DATA, LombokClassNames.VALUE);
    }
    return true;
  }
}
