package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author ven
 */
public abstract class LightVariableBase extends LightElement implements PsiVariable {
  protected PsiElement myScope;
  protected PsiIdentifier myNameIdentifier;
  protected final PsiType myType;
  protected PsiModifierList myModifierList;
  protected boolean myWritable;

  public LightVariableBase(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, StdFileTypes.JAVA.getLanguage());
    myModifierList = new LightModifierList(myManager);
    myNameIdentifier = nameIdentifier;
    myWritable = writable;
    myType = type;
    myScope = scope;
  }

  public PsiElement getDeclarationScope() {
    return myScope;
  }

  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  public boolean isValid() {
    return myNameIdentifier == null || myNameIdentifier.isValid();
  }

  public String getName() {
    return getNameIdentifier().getText();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException{
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public PsiType getType() {
    return myType;
  }

  public PsiTypeElement getTypeElement() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeElement(myType);
  }

  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  public PsiExpression getInitializer() {
    return null;
  }

  public boolean hasInitializer() {
    return false;
  }

  public String getText() {
    return myNameIdentifier.getText();
  }

  public PsiElement copy() {
    return null;
  }

  public Object computeConstantValue() {
    return null;
  }

  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  public boolean isWritable() {
    return myWritable;
  }

  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = createLayeredIcon(Icons.VARIABLE_ICON, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }
}
