package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;

import java.awt.*;

class GotoFileCellRenderer extends PsiElementListCellRenderer{
  public GotoFileCellRenderer() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Font editorFont = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
    setFont(editorFont);
  }

  public String getElementText(PsiElement element) {
    final PsiFile file = (PsiFile)element;
    return file.getName();
  }

  protected String getContainerText(PsiElement element) {
    PsiFile file = (PsiFile)element;
    String path = "(" + file.getContainingDirectory().getVirtualFile().getPresentableUrl() + ")";
    return path;
  }

  protected int getIconFlags() {
    return 0;
  }
}