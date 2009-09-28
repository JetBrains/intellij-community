package org.jetbrains.idea.maven.dom.references;

import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.RenameableFakePsiElement;
import org.jetbrains.idea.maven.utils.MavenIcons;

import javax.swing.*;

public class MavenPsiElementWrapper extends RenameableFakePsiElement {
  private final PsiElement myWrappee;
  private final Navigatable myNavigatable;

  public MavenPsiElementWrapper(PsiElement wrappeeElement, Navigatable navigatable) {
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
    if (other instanceof MavenPsiElementWrapper) {
      return myWrappee == ((MavenPsiElementWrapper)other).myWrappee;
    }
    return myWrappee == other;
  }
}
