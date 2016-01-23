package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PropertyUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class LombokGetterHandler extends BaseLombokHandler {

  protected void processClass(@NotNull PsiClass psiClass) {
    final Map<PsiField, PsiMethod> fieldMethodMap = new HashMap<PsiField, PsiMethod>();
    for (PsiField psiField : psiClass.getFields()) {
      PsiMethod propertySetter = PropertyUtil.findPropertyGetter(psiClass, psiField.getName(), psiField.hasModifierProperty(PsiModifier.STATIC), false);

      if (null != propertySetter) {
        fieldMethodMap.put(psiField, propertySetter);
      }
    }

    processIntern(fieldMethodMap, psiClass, Getter.class);
  }

}
