package com.intellij.ide;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiElement;

import java.awt.datatransfer.Transferable;

/**
 * @author max
 */
public class CopyPasteUtil {
  private CopyPasteUtil() { }

  public static PsiElement[] getElementsInTransferable(Transferable t) {
    final PsiElement[] elts = ((CopyPasteManagerEx)CopyPasteManager.getInstance()).getElements(t);
    return elts != null ? elts : PsiElement.EMPTY_ARRAY;
  }
}
