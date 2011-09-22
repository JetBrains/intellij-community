package de.plushnikov.intellij.lombok.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightFieldBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class MyLightFieldBuilder extends LightFieldBuilder {
  private boolean hasInitializer;

  public MyLightFieldBuilder(@NotNull String name, @NotNull String type, @NotNull PsiElement navigationElement) {
    super(name, type, navigationElement);
  }

  public MyLightFieldBuilder(@NotNull String name, @NotNull PsiType type, @NotNull PsiElement navigationElement) {
    super(name, type, navigationElement);
  }

  public MyLightFieldBuilder(PsiManager manager, @NotNull String name, @NotNull PsiType type) {
    super(manager, name, type);
  }

  @Override
  public boolean hasInitializer() {
    return hasInitializer;
  }

  public MyLightFieldBuilder setHasInitializer(boolean value) {
    hasInitializer = value;
    return this;
  }
}
