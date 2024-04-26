package de.plushnikov.intellij.plugin.psi;

import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LombokLightAnnotationMethodBuilder extends LombokLightMethodBuilder implements PsiAnnotationMethod {
  private PsiAnnotationMemberValue defaultValue;

  public LombokLightAnnotationMethodBuilder(@NotNull PsiManager manager, @NotNull String name) {
    super(manager, name);
  }

  public LombokLightAnnotationMethodBuilder withDefaultValue(PsiAnnotationMemberValue defaultValue) {
    this.defaultValue = defaultValue;
    return this;
  }

  @Override
  public @Nullable PsiAnnotationMemberValue getDefaultValue() {
    return defaultValue;
  }
}
