package com.intellij.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

public class XmlPsiTreeChangePreprocessor extends PsiTreeChangePreprocessorBase {
  public XmlPsiTreeChangePreprocessor(@NotNull PsiManager psiManager) {
    super(psiManager);
  }

  @Override
  protected boolean acceptsEvent(@NotNull PsiTreeChangeEventImpl event) {
    return event.getFile() instanceof XmlFile;
  }

  @Override
  protected boolean isOutOfCodeBlock(@NotNull PsiElement element) {
    // any xml element isn't inside a "code block"
    // cause we display even attributes and tag values in structure view
    return element.getLanguage() instanceof XMLLanguage;
  }

}
