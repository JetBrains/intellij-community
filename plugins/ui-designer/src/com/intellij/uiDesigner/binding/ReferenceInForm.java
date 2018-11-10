// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ReferenceInForm implements PsiReference {
  protected final PsiPlainTextFile myFile;
  private final RangeMarker myRangeMarker;

  protected ReferenceInForm(final PsiPlainTextFile file, TextRange range) {
    myFile = file;
    final Document document = FileDocumentManager.getInstance().getDocument(myFile.getViewProvider().getVirtualFile());
    myRangeMarker = document.createRangeMarker(range);
  }

  @Override
  @NotNull
  public PsiElement getElement() {
    return myFile;
  }

  @Override
  public PsiElement handleElementRename(@NotNull final String newElementName){
    return handleElementRenameBase(newElementName);
  }

  private PsiElement handleElementRenameBase(final String newElementName) {
    updateRangeText(newElementName);
    return myFile;
  }

  @Override
  @NotNull
  public TextRange getRangeInElement() {
    return TextRange.create(myRangeMarker);
  }

  @Override
  @NotNull
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

  @Override
  public boolean isReferenceTo(@NotNull final PsiElement element) {
    return resolve() == element;
  }

  @Override
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
