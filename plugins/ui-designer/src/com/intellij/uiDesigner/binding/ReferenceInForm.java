/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 5, 2005
 */
public abstract class ReferenceInForm implements PsiReference {
  protected final PsiPlainTextFile myFile;
  private final RangeMarker myRangeMarker;

  protected ReferenceInForm(final PsiPlainTextFile file, TextRange range) {
    myFile = file;
    final Document document = FileDocumentManager.getInstance().getDocument(myFile.getVirtualFile());
    myRangeMarker = document.createRangeMarker(range);
  }

  public PsiElement getElement() {
    return myFile;
  }

  public PsiElement handleElementRename(final String newElementName){
    return handleElementRenameBase(newElementName);
  }

  private PsiElement handleElementRenameBase(final String newElementName) {
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

  protected PsiElement handleFileRename(final String newElementName, @NonNls final String extension,
                                        final boolean includeExtensionInReference) {
    final String currentName = getRangeText();
    final String baseName = newElementName.endsWith(extension)?
                            newElementName.substring(0, newElementName.length() - extension.length()) :
                            newElementName;
    final int slashIndex = currentName.lastIndexOf('/');
    final String extensionInReference = includeExtensionInReference ? extension : "";
    if (slashIndex >= 0) {
      final String prefix = currentName.substring(0, slashIndex);
      return handleElementRenameBase(prefix + "/" + baseName + extensionInReference);
    }
    else {
      return handleElementRenameBase(baseName + extensionInReference);
    }
  }
}
