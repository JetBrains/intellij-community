// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.RenameableFakePsiElement;
import org.jetbrains.idea.maven.dom.MavenDomBundle;

import javax.swing.*;

import static icons.OpenapiIcons.RepositoryLibraryLogo;

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

  @Override
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

  @Override
  public String getTypeName() {
    return MavenDomBundle.message("maven.type.name.property");
  }

  @Override
  public Icon getIcon() {
    return RepositoryLibraryLogo;
  }

  @Override
  public TextRange getTextRange() {
    return myWrappee.getTextRange();
  }

  @Override
  public boolean isEquivalentTo(PsiElement other) {
    if (other instanceof MavenPsiElementWrapper) {
      return myWrappee == ((MavenPsiElementWrapper)other).myWrappee;
    }
    return myWrappee == other;
  }
}
