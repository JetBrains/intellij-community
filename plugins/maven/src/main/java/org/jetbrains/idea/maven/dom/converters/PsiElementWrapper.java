package org.jetbrains.idea.maven.dom.converters;

import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.RenameableFakePsiElement;
import org.jetbrains.idea.maven.utils.MavenIcons;

import javax.swing.*;

public class PsiElementWrapper extends RenameableFakePsiElement {
  private final PsiElement myWrappee;
  private final Navigatable myNavigatable;

  public PsiElementWrapper(PsiElement wrappeeElement, Navigatable navigatable) {
    super(wrappeeElement.getParent());
    myWrappee = wrappeeElement;
    myNavigatable = navigatable;
  }

  public PsiElement getWrappee() {
    return myWrappee;
  }

  public PsiElement getParent() {
    return myWrappee.getParent();
  }

  @Override
  public String getName() {
    return ((PsiNamedElement)myWrappee).getName();
  }

  @Override
  public void navigate(boolean requestFocus) {
    myNavigatable.navigate(requestFocus);
  }

  public String getTypeName() {
    return "Property";
  }

  public Icon getIcon() {
    return MavenIcons.MAVEN_ICON;
  }

  @Override
  public boolean isEquivalentTo(PsiElement other) {
    if (other instanceof PsiElementWrapper) {
      return myWrappee == ((PsiElementWrapper)other).myWrappee;
    }
    return myWrappee == other;
  }
}
