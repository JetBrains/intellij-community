package org.jetbrains.idea.maven.dom;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.FakePsiElement;

public class MavenUrlReference extends MavenPsiReference {
  public MavenUrlReference(PsiElement element,
                           String originalText,
                           String resolvedText,
                           TextRange range) {
    super(element, originalText, resolvedText, range);
  }

  public PsiElement resolve() {
    return new FakePsiElement() {
      public PsiElement getParent() {
        return myElement;
      }

      @Override
      public String getName() {
        return myResolvedText;
      }

      @Override
      public void navigate(boolean requestFocus) {
        BrowserUtil.launchBrowser(myResolvedText);
      }
    };
  }

  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}