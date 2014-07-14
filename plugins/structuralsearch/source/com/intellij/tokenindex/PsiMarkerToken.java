package com.intellij.tokenindex;

import com.intellij.psi.PsiFile;

/**
 * @author Eugene.Kudelevsky
 */
public class PsiMarkerToken extends Token {
  private final PsiFile myFile;

  public PsiMarkerToken(PsiFile file) {
    super(-1, -1);
    myFile = file;
  }

  public PsiFile getFile() {
    return myFile;
  }
}
