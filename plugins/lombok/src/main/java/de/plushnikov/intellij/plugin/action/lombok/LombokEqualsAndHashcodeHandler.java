package de.plushnikov.intellij.plugin.action.lombok;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

public class LombokEqualsAndHashcodeHandler extends BaseLombokHandler {

  @Override
  protected void processClass(@NotNull PsiClass psiClass) {
    final PsiMethod equalsMethod = findPublicNonStaticMethod(psiClass, "equals", PsiType.BOOLEAN,
      PsiType.getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope()));
    if (null != equalsMethod) {
      equalsMethod.delete();
    }

    final PsiMethod hashCodeMethod = findPublicNonStaticMethod(psiClass, "hashCode", PsiType.INT);
    if (null != hashCodeMethod) {
      hashCodeMethod.delete();
    }

    addAnnotation(psiClass, LombokClassNames.EQUALS_AND_HASHCODE);
  }
}
