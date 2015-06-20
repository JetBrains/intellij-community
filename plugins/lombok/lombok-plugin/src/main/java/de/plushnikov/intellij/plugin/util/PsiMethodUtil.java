package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Plushnikov Michail
 */
public class PsiMethodUtil {
  @NotNull
  public static PsiCodeBlock createCodeBlockFromText(@NotNull String blockText, @NotNull PsiClass psiClass) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
    return elementFactory.createCodeBlockFromText("{" + blockText + "}", psiClass);
  }

  @Nullable
  public static PsiTypeParameterList createTypeParameterList(@NotNull PsiTypeParameterList psiTypeParameterList) {
    PsiTypeParameter[] psiTypeParameters = psiTypeParameterList.getTypeParameters();
    if (psiTypeParameters.length > 0) {

      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiTypeParameterList.getProject()).getElementFactory();

      final StringBuilder builder = new StringBuilder("public <");

      for (PsiTypeParameter psiTypeParameter : psiTypeParameters) {
        builder.append(psiTypeParameter.getName());

        PsiClassType[] superTypes = psiTypeParameter.getExtendsListTypes();
        if (superTypes.length > 1 || superTypes.length == 1 && !superTypes[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          builder.append(" extends ");
          for (PsiClassType type : superTypes) {
            if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
              continue;
            }
            builder.append(type.getCanonicalText()).append('&');
          }
          builder.deleteCharAt(builder.length() - 1);
        }
        builder.append(',');
      }
      builder.deleteCharAt(builder.length() - 1);

      builder.append("> void foo(){}");

      PsiMethod methodFromText = elementFactory.createMethodFromText(builder.toString(), null);
      return methodFromText.getTypeParameterList();
    }
    return null;
  }


  public static boolean hasMethodByName(@NotNull Collection<PsiMethod> classMethods, @NotNull String methodName) {
    boolean hasMethod = false;
    for (PsiMethod classMethod : classMethods) {
      if (methodName.equals(classMethod.getName())) {
        hasMethod = true;
        break;
      }
    }
    return hasMethod;
  }

  public static boolean hasMethodByName(@NotNull Collection<PsiMethod> classMethods, String... methodNames) {
    boolean hasMethod = false;
    for (String methodName : methodNames) {
      if (hasMethodByName(classMethods, methodName)) {
        hasMethod = true;
        break;
      }
    }
    return hasMethod;
  }

  public static boolean hasSimilarMethod(@NotNull Collection<PsiMethod> classMethods, @NotNull String methodName, int methodArgCount) {
    for (PsiMethod classMethod : classMethods) {
      if (isSimilarMethod(classMethod, methodName, methodArgCount)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isSimilarMethod(@NotNull PsiMethod classMethod, @NotNull String methodName, int methodArgCount) {
    boolean equalNames = methodName.equalsIgnoreCase(classMethod.getName());

    int parametersCount = classMethod.getParameterList().getParametersCount();
    if (classMethod.isVarArgs()) {
      parametersCount--;
    }

    return equalNames && methodArgCount == parametersCount;
  }
}
