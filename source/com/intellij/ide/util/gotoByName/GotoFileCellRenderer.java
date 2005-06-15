package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.FilePathSplittingPolicy;

import java.awt.*;

public class GotoFileCellRenderer extends PsiElementListCellRenderer<PsiFile>{
  private final int myMaxWidth;

  public GotoFileCellRenderer(int maxSize) {
    myMaxWidth = maxSize;
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Font editorFont = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
    setFont(editorFont);
  }

  public String getElementText(PsiFile element) {
    return element.getName();
  }

  protected String getContainerText(PsiElement element, String name) {
    PsiFile file = (PsiFile)element;
    final VirtualFile virtualFile = file.getContainingDirectory().getVirtualFile();
    final String prefix = name + "     ";

    String path = FilePathSplittingPolicy.SPLIT_BY_SEPARATOR.getOptimalTextForComponent(prefix, VfsUtil.virtualToIoFile(virtualFile), this, myMaxWidth);
    return "(" + path + ")";
  }

  protected int getIconFlags() {
    return 0;
  }
}