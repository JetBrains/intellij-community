package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.Language;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Plushnikov Michail
 */
public abstract class LombokLightVariableBuilder extends LightElement implements PsiVariable {
  protected final String myName;
  protected final PsiType myType;
  protected final LombokLightModifierList myModifierList;
  protected PsiElement myNavigationElement;
  private volatile Icon myBaseIcon = Icons.VARIABLE_ICON;

  public LombokLightVariableBuilder(@NotNull PsiManager manager, @NotNull String name, @NotNull PsiType type, @NotNull Language language) {
    super(manager, language);
    myName = name;
    myType = type;
    myModifierList = new LombokLightModifierList(manager, language, this);
    myNavigationElement = this;
  }

  public LombokLightVariableBuilder addModifier(@Modifier @NotNull @NonNls String modifier) {
    myModifierList.addModifier(modifier);
    return this;
  }

  public LombokLightVariableBuilder setNavigationElement(PsiElement navigationElement) {
    myNavigationElement = navigationElement;
    return this;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  public LombokLightVariableBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }

  @Override
  public String toString() {
    return "LightVariableBuilder:" + getName();
  }

  @NotNull
  @Override
  public PsiType getType() {
    return myType;
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public boolean hasModifierProperty(@Modifier @NonNls @NotNull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getText() {
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {

  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return null;
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return null != getInitializer();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("setName is not implemented yet in com.intellij.psi.impl.light.LightVariableBuilder");
  }


  public PsiType getTypeNoResolve() {
    return getType();
  }

  protected boolean isVisibilitySupported() {
    return true;
  }

  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = createLayeredIcon(myBaseIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }
}
