package de.plushnikov.intellij.lombok.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.light.LightClass;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface LombokLightClassBuilder extends PsiClass {

    LombokLightClassBuilder withModifier(@NotNull @NonNls String modifier);
}
