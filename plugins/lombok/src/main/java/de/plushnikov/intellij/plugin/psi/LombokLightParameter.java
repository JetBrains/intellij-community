package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.impl.light.LightParameter;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * @author Plushnikov Michail
 */
public class LombokLightParameter extends LightParameter implements SyntheticElement {
  private final LombokLightIdentifier myNameIdentifier;

  public LombokLightParameter(@NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope) {
    this(name, type, declarationScope, JavaLanguage.INSTANCE);
  }

  public LombokLightParameter(@NotNull String name,
                              @NotNull PsiType type,
                              @NotNull PsiElement declarationScope,
                              @NotNull Language language) {
    super(name, type, declarationScope, language, new LombokLightModifierList(declarationScope.getManager(), language));
    myNameIdentifier = new LombokLightIdentifier(declarationScope.getManager(), name);
  }

  @NotNull
  @Override
  public String getName() {
    return myNameIdentifier.getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) {
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

  @Override
  public LombokLightParameter setModifiers(String... modifiers) {
    final LombokLightModifierList lombokLightModifierList = (LombokLightModifierList)getModifierList();
    lombokLightModifierList.clearModifiers();
    Stream.of(modifiers).forEach(lombokLightModifierList::addModifier);
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

    LombokLightParameter that = (LombokLightParameter)o;

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
