package de.plushnikov.intellij.plugin.extension;

import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyAccessorDetector;
import com.intellij.psi.util.PropertyUtilBase;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LombokPropertyAccessorDetector implements PropertyAccessorDetector {

  @Override
  public @Nullable PropertyAccessorInfo detectPropertyAccessor(@NotNull PsiMethod method) {
    if (method instanceof LombokLightMethodBuilder methodBuilder) {
      final PsiElement navigationElement = methodBuilder.getNavigationElement();
      if (navigationElement instanceof PsiField originalField) {
        final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(originalField);

        final boolean lombokPropertySetterOrWither = isLombokPropertySetterOrWither(methodBuilder, originalField, accessorsInfo);
        final boolean lombokPropertyGetter = isLombokPropertyGetter(methodBuilder, originalField, accessorsInfo);


        if (lombokPropertySetterOrWither || lombokPropertyGetter) {
          return new PropertyAccessorInfo(PropertyUtilBase.suggestPropertyName(originalField),
                                          originalField.getType(),
                                          lombokPropertyGetter?PropertyKind.GETTER:PropertyKind.SETTER);
        }
      }
    }
    return null;
  }

  private static boolean isLombokPropertyGetter(LombokLightMethodBuilder method, PsiField originalField, AccessorsInfo accessorsInfo) {
    return !method.hasParameters() && method.getName().equals(LombokUtils.getGetterName(originalField, accessorsInfo));
  }

  private static boolean isLombokPropertySetterOrWither(LombokLightMethodBuilder method,
                                                        PsiField originalField,
                                                        AccessorsInfo accessorsInfo) {
    return method.getParameterList().getParameters().length == 1 &&
           (method.getName().equals(LombokUtils.getSetterName(originalField, accessorsInfo)) ||
            method.getName().equals(LombokUtils.getWitherName(originalField, accessorsInfo)));
  }
}
