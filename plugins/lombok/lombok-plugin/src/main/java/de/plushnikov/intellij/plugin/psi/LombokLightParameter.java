package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.light.LightVariableBuilder;
import de.plushnikov.intellij.plugin.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class LombokLightParameter extends LightParameter {
  private String myName;
  private final LombokLightIdentifier myNameIdentifier;

  public LombokLightParameter(@NotNull String name, @NotNull PsiType type, PsiElement declarationScope, Language language) {
    super(name, type, declarationScope, language);
    myName = name;
    PsiManager manager = declarationScope.getManager();
    myNameIdentifier = new LombokLightIdentifier(manager, name);
    ReflectionUtil.setFinalFieldPerReflection(LightVariableBuilder.class, this, LightModifierList.class,
        new LombokLightModifierList(manager, language));
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(@NotNull String name) {
    myName = name;
    myNameIdentifier.setText(name);
    return this;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  public LombokLightParameter setModifiers(String... modifiers) {
    LombokLightModifierList modifierList = new LombokLightModifierList(getManager(), getLanguage(), modifiers);
    ReflectionUtil.setFinalFieldPerReflection(LightVariableBuilder.class, this, LightModifierList.class, modifierList);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LombokLightParameter that = (LombokLightParameter) o;

    return getType().isValid() == that.getType().isValid() && getType().equals(that.getType());
  }

  @Override
  public int hashCode() {
    return getType().hashCode();
  }
}
