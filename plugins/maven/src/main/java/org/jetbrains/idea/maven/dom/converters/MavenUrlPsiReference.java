package org.jetbrains.idea.maven.dom.converters;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.FakePsiElement;

public class MavenUrlPsiReference extends MavenPsiReference {
  public MavenUrlPsiReference(PsiElement element, String text, TextRange range) {
    super(element, text, range);
  }

  public PsiElement resolve() {
    return new FakePsiElement() {
      public PsiElement getParent() {
        return myElement;
      }

      @Override
      public String getName() {
        return myText;
      }

      @Override
      public void navigate(boolean requestFocus) {
        BrowserUtil.launchBrowser(myText);
      }
    };
  }

  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}