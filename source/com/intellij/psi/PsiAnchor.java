package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Mar 26, 2004
 * Time: 7:03:41 PM
 * To change this template use File | Settings | File Templates.
 */
public class PsiAnchor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiAnchor");
  private Class myClass;
  private int myStartOffset;
  private int myEndOffset;
  private PsiFile myFile;
  private int myRootIndex = -1;
  private PsiElement myElement;

  public PsiAnchor(PsiElement element) {
    LOG.assertTrue(element.isValid());

    if (element instanceof PsiCompiledElement || element instanceof PsiMember) {
      myElement = element;
    }
    else {
      myElement = null;
      myFile = element.getContainingFile();

      if (myFile == null) {
        myElement = element;
        return;
      }

      final PsiFile[] psiRoots = myFile.getPsiRoots();
      for (int i = 0; i < psiRoots.length; i++) {
        PsiFile root = psiRoots[i];
        if (PsiTreeUtil.isAncestor(root, element,false)) {
          myRootIndex = i;
          break;
        }
      }
      LOG.assertTrue(myRootIndex >= 0);

      myClass = element.getClass();

      TextRange textRange = element.getTextRange();

      if (textRange != null) {
        myStartOffset = textRange.getStartOffset();
        myEndOffset = textRange.getEndOffset();
      }
    }
  }

  public PsiElement retrieve() {
    if (myElement != null) return myElement;

    PsiElement element = myFile.getPsiRoots()[myRootIndex].findElementAt(myStartOffset);
    if (element == null) return null;

    while  (!element.getClass().equals(myClass) ||
            element.getTextRange().getStartOffset() != myStartOffset ||
            element.getTextRange().getEndOffset() != myEndOffset) {
      element = element.getParent();
      if (element == null || element.getTextRange() == null) return null;
    }

    return element;
  }

  public PsiFile getFile() {
    return myElement != null ? myElement.getContainingFile() : myFile;
  }

  public int getStartOffset() {
    return myElement != null ? myElement.getTextRange().getStartOffset() : myStartOffset;
  }

  public int getEndOffset() {
    return myElement != null ? myElement.getTextRange().getEndOffset() : myEndOffset;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PsiAnchor)) return false;

    final PsiAnchor psiAnchor = (PsiAnchor)o;

    if (psiAnchor.myElement != null && myElement != null) {
      return psiAnchor.myElement.equals(myElement);
    }

    if (psiAnchor.myElement == null && myElement == null) {
      if (myEndOffset != psiAnchor.myEndOffset) return false;
      if (myStartOffset != psiAnchor.myStartOffset) return false;
      if (myClass != null ? !myClass.equals(psiAnchor.myClass) : psiAnchor.myClass != null) return false;
      if (myFile != null ? !myFile.equals(psiAnchor.myFile) : psiAnchor.myFile != null) return false;

      return true;
    }
    else {
      return false;
    }
  }

  public int hashCode() {
    int result;

    if (myElement != null){
      return myElement.hashCode();
    }

    result = (myClass != null ? myClass.hashCode() : 0);
    result = 29 * result + myStartOffset;
    result = 29 * result + myEndOffset;
    result = 29 * result + (myFile != null ? myFile.hashCode() : 0);

    return result;
  }
}

