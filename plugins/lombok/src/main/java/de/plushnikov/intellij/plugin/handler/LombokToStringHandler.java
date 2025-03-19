package de.plushnikov.intellij.plugin.handler;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

public class LombokToStringHandler extends BaseLombokHandler {

  @Override
  protected void processClass(@NotNull PsiClass psiClass) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    final PsiClassType stringClassType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, psiClass.getResolveScope());

    final PsiMethod toStringMethod = findPublicNonStaticMethod(psiClass, "toString", stringClassType);
    if (null != toStringMethod) {
      toStringMethod.delete();
    }
    addAnnotation(psiClass, LombokClassNames.TO_STRING);
  }

}
