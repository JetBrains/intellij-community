package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightParameter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Plushnikov Michail
 */
public class LombokLightParameter extends LightParameter implements SyntheticElement {
  private final LombokLightIdentifier myNameIdentifier;
  private PsiElement myParent;

  public LombokLightParameter(@NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope) {
    this(name, type, declarationScope, JavaLanguage.INSTANCE);
  }

  public LombokLightParameter(@NotNull String name,
                              @NotNull PsiType type,
                              @NotNull PsiElement declarationScope,
                              @NotNull Language language) {
    super(name, type, declarationScope, language, new LombokLightModifierList(declarationScope.getManager(), language), type instanceof PsiEllipsisType);
    getModifierList().withParent(this);
    myNameIdentifier = new LombokLightIdentifier(declarationScope.getManager(), name);
  }

  @Override
  public @NotNull String getName() {
    return myNameIdentifier.getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) {
    myNameIdentifier.setText(name);
    return this;
  }

  public void setParent(PsiElement parent) {
    myParent = parent;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  @Override
  public @NotNull LombokLightModifierList getModifierList() {
    return (LombokLightModifierList)super.getModifierList();
  }

  public LombokLightParameter withAnnotation(@NotNull String annotation) {
    getModifierList().addAnnotation(annotation);
    return this;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  @Override
  public @NotNull LombokLightParameter setModifiers(@NotNull String @NotNull ... modifiers) {
    final LombokLightModifierList lombokLightModifierList = getModifierList();
    lombokLightModifierList.clearModifiers();
    Stream.of(modifiers).forEach(lombokLightModifierList::addModifier);
    return this;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
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
    if (!Objects.equals(getName(), that.getName())) {
      return false;
    }

    DumbService dumbService = DumbService.getInstance(getProject());
    if (dumbService.isDumb() && !dumbService.isAlternativeResolveEnabled()) {
      //it is necessary to resolve for weak references for inner caches
      return dumbService.computeWithAlternativeResolveEnabled(() -> {
        return getType().equals(that.getType());
      });
    }
    else {
      return getType().equals(that.getType());
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getType());
  }
}
