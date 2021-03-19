package de.plushnikov.intellij.plugin.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.IncorrectOperationException;
import icons.LombokIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Plushnikov Michail
 */
public class LombokLightFieldBuilder extends LightFieldBuilder implements SyntheticElement {
  private String myName;
  private final LombokLightIdentifier myNameIdentifier;
  private final LombokLightModifierList myModifierList;

  public LombokLightFieldBuilder(@NotNull PsiManager manager, @NotNull String name, @NotNull PsiType type) {
    super(manager, name, type);
    myName = name;
    myNameIdentifier = new LombokLightIdentifier(manager, name);
    myModifierList = new LombokLightModifierList(manager);
    setBaseIcon(LombokIcons.Nodes.LombokField);
  }

  @Override
  @NotNull
  public LombokLightModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public LombokLightFieldBuilder setModifiers(String... modifiers) {
    myModifierList.clearModifiers();
    Stream.of(modifiers).forEach(myModifierList::addModifier);
    return this;
  }

  @Override
  public LombokLightFieldBuilder setModifierList(LightModifierList modifierList) {
    setModifiers(modifierList.getModifiers());
    return this;
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    return containingClass != null ? containingClass.getContainingFile() : null;
  }

  public LombokLightFieldBuilder withContainingClass(PsiClass psiClass) {
    setContainingClass(psiClass);
    return this;
  }

  public LombokLightFieldBuilder withImplicitModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    myModifierList.addImplicitModifierProperty(modifier);
    return this;
  }

  public LombokLightFieldBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    myModifierList.addModifier(modifier);
    return this;
  }

  public LombokLightFieldBuilder withNavigationElement(PsiElement navigationElement) {
    setNavigationElement(navigationElement);
    return this;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(@NotNull String name) {
    myName = name;
    myNameIdentifier.setText(myName);
    return this;
  }

  @NotNull
  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  public String toString() {
    return "LombokLightFieldBuilder: " + getName();
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    // just add new element to the containing class
    final PsiClass containingClass = getContainingClass();
    if (null != containingClass) {
      CheckUtil.checkWritable(containingClass);
      return containingClass.add(newElement);
    }
    return null;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  @Override
  public void delete() throws IncorrectOperationException {
    // simple do nothing
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    // simple do nothing
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (another instanceof LombokLightFieldBuilder) {
      final LombokLightFieldBuilder anotherLightField = (LombokLightFieldBuilder) another;

      boolean stillEquivalent = getName().equals(anotherLightField.getName()) &&
        getType().equals(anotherLightField.getType());

      if (stillEquivalent) {
        final PsiClass containingClass = getContainingClass();
        final PsiClass anotherContainingClass = anotherLightField.getContainingClass();

        stillEquivalent = (null == containingClass && null == anotherContainingClass) ||
          (null != containingClass && containingClass.isEquivalentTo(anotherContainingClass));
      }

      return stillEquivalent;
    } else {
      return super.isEquivalentTo(another);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LombokLightFieldBuilder that = (LombokLightFieldBuilder) o;
    return
      Objects.equals(myName, that.myName) &&
      Objects.equals(myNameIdentifier, that.myNameIdentifier) &&
      Objects.equals(myModifierList, that.myModifierList) &&
      Objects.equals(getContainingClass(), that.getContainingClass());
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myNameIdentifier, myModifierList, getContainingClass());
  }
}
