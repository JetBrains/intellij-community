package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;

import java.awt.*;

public class GotoFileCellRenderer extends PsiElementListCellRenderer<PsiFile>{
  public GotoFileCellRenderer() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Font editorFont = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
    setFont(editorFont);
  }

  public String getElementText(PsiFile element) {
    return element.getName();
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