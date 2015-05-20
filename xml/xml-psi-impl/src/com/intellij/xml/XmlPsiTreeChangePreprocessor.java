package com.intellij.xml;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

public class XmlPsiTreeChangePreprocessor extends PsiTreeChangePreprocessorBase {
  public XmlPsiTreeChangePreprocessor(@NotNull Project project) {
    super(project);
  }

  @Override
  protected boolean isInsideCodeBlock(PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return false;
    }

    if (element == null || element.getParent() == null) return true;

    final boolean isXml = element.getLanguage() instanceof XMLLanguage;
    // any xml element isn't inside a "code block"
    // cause we display even attributes and tag values in structure view
    return !isXml;
  }

  @Override
  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (!(event.getFile() instanceof XmlFile)) return;
    super.treeChanged(event);
  }
}
