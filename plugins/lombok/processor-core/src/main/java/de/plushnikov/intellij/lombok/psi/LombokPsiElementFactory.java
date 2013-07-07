package de.plushnikov.intellij.lombok.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class LombokPsiElementFactory {
  private static final Logger LOG = Logger.getInstance(LombokPsiElementFactory.class.getName());

  private LombokPsiElementFactory() {
  }

  private static LombokPsiElementFactory ourInstance;

  public static LombokPsiElementFactory getInstance() {
    if (null == ourInstance) {
      ourInstance = new LombokPsiElementFactory();
    }
    return ourInstance;
  }

  public LombokLightFieldBuilder createLightField(@NotNull PsiManager manager, @NotNull String fieldName, @NotNull PsiType fieldType) {
    return new LombokLightFieldBuilderImpl(manager, fieldName, fieldType);
  }

  public LombokLightMethodBuilder createLightMethod(@NotNull PsiManager manager, @NotNull String methodName) {
    return new LombokLightMethodBuilderImpl(manager, methodName);
  }

  public LombokLightMethod createLightMethod(@NotNull PsiManager manager, @NotNull PsiMethod valuesMethod, @NotNull PsiClass psiClass) {
    return new LombokLightMethodImpl(manager, valuesMethod, psiClass);
  }
}
