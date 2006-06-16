/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 5, 2005
 */
public abstract class ReferenceInForm implements PsiReference {
  protected final PsiPlainTextFile myFile;
  protected RangeMarkerImpl myRangeMarker;

  protected ReferenceInForm(final PsiPlainTextFile file, TextRange range) {
    myFile = file;
    final Document document = FileDocumentManager.getInstance().getDocument(myFile.getVirtualFile());
    myRangeMarker = new RangeMarkerImpl(document, range.getStartOffset(), range.getEndOffset());
  }

  public PsiElement getElement() {
    return myFile;
  }

  public PsiElement handleElementRename(final String newElementName){
    updateRangeText(newElementName);
    return myFile;
  }

  public TextRange getRangeInElement() {
    return new TextRange(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset());
  }

  public String getCanonicalText() {
    return getRangeText();
  }

  protected void updateRangeText(final String text) {
    final Document document = myRangeMarker.getDocument();
    document.replaceString(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset(), text);
    PsiDocumentManager.getInstance(myFile.getProject()).commitDocument(document);
  }

  public String getRangeText() {
    return myRangeMarker.getDocument().getCharsSequence().subSequence(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset()).toString();
  }

  public boolean isReferenceTo(final PsiElement element) {
    return resolve() == element;
  }

  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return true;
  }
}
