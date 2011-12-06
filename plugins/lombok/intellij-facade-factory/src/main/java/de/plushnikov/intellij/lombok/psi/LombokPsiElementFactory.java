package de.plushnikov.intellij.lombok.psi;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class LombokPsiElementFactory {
  private static LombokPsiElementFactory ourInstance = new LombokPsiElementFactory();

  public static LombokPsiElementFactory getInstance() {
    return ourInstance;
  }

  private LombokPsiElementFactory() {
  }

  public LombokLightFieldBuilder createLightField(@NotNull PsiManager manager, @NotNull String fieldName, @NotNull PsiType fieldType) {
    return new LombokLightFieldBuilder9Impl(manager, fieldName, fieldType);
  }

  public LombokLightMethodBuilder createLightMethod(@NotNull PsiManager manager, @NotNull String methodName) {
    return new LombokLightMethodBuilder9Impl(manager, methodName);
  }
}
