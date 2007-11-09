package com.intellij.ide;

import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiElement;

import java.awt.datatransfer.Transferable;

/**
 * @author max
 */
public class CopyPasteUtil {
  private CopyPasteUtil() { }

  public static PsiElement[] getElementsInTransferable(Transferable t) {
    final PsiElement[] elts = PsiCopyPasteManager.getElements(t);
    return elts != null ? elts : PsiElement.EMPTY_ARRAY;
  }

  public static class DefaultCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    private AbstractTreeUpdater myUpdater;

    public DefaultCopyPasteListener(final AbstractTreeUpdater updater) {
      myUpdater = updater;
    }

    public void contentChanged(final Transferable oldTransferable, final Transferable newTransferable) {
      updateByTransferable(oldTransferable);
      updateByTransferable(newTransferable);
    }

    private void updateByTransferable(final Transferable t) {
      final PsiElement[] psiElements = CopyPasteUtil.getElementsInTransferable(t);
      for (PsiElement psiElement : psiElements) {
        myUpdater.addSubtreeToUpdateByElement(psiElement);
      }
    }
  }
}
