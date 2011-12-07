package de.plushnikov.intellij.lombok.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightModifierList;

/**
 * @author Plushnikov Michail
 */
public class LombokLightFieldBuilder11Impl extends LightFieldBuilder implements LombokLightFieldBuilder {
  public LombokLightFieldBuilder11Impl(@NotNull PsiManager manager, @NotNull String name, @NotNull PsiType type) {
    super(manager, name, type);
  }

  @Override
  public LombokLightFieldBuilder withContainingClass(PsiClass psiClass) {
    setContainingClass(psiClass);
    return this;
  }

  @Override
  public LombokLightFieldBuilder withModifier(@Modifier @NotNull @NonNls String modifier) {
    ((LightModifierList) getModifierList()).addModifier(modifier);
    return this;
  }

  @Override
  public LombokLightFieldBuilder withNavigationElement(PsiElement navigationElement) {
    setNavigationElement(navigationElement);
    return this;
  }

  public FileStatus getFileStatus() {
    return FileStatus.NOT_CHANGED;
  }

  public String toString() {
    return "LombokLightFieldBuilder: " + getName();
  }
}
