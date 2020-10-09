package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.psi.*;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

public class LombokToStringHandler extends BaseLombokHandler {

  protected void processClass(@NotNull PsiClass psiClass) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    final PsiClassType stringClassType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, psiClass.getResolveScope());

    final PsiMethod toStringMethod = findPublicNonStaticMethod(psiClass, "toString", stringClassType);
    if (null != toStringMethod) {
      toStringMethod.delete();
    }
    addAnnotation(psiClass, ToString.class);
  }

}
