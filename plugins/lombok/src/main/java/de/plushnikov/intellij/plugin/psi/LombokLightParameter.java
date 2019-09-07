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

import java.util.Collections;

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
      new LombokLightModifierList(manager, language, Collections.emptySet()));
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
    LombokLightModifierList modifierList = new LombokLightModifierList(getManager(), getLanguage(), Collections.emptySet(), modifiers);
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

    final PsiType thisType = getType();
    final PsiType thatType = that.getType();
    if (thisType.isValid() != thatType.isValid()) {
      return false;
    }

    return thisType.getCanonicalText().equals(thatType.getCanonicalText());
  }

  @Override
  public int hashCode() {
    return getType().hashCode();
  }
}
