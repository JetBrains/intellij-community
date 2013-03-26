package com.jetbrains.python.refactoring.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapDescriptorBase;
import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.Nullable;

/**
 * user : ktisha
 */
public class PyUnwrapDescriptor extends UnwrapDescriptorBase{
  @Override
  protected Unwrapper[] createUnwrappers() {
    return new Unwrapper[] {
      new PyIfUnwrapper(),
      new PyWhileUnwrapper(),
      new PyElseRemover(),
      new PyElseUnwrapper(),
      new PyElIfUnwrapper(),
      new PyElIfRemover(),
      new PyTryUnwrapper(),
      new PyForUnwrapper(),
      new PyWithUnwrapper()
    };
  }

  @Override
  @Nullable
  protected PsiElement findTargetElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement endElement = PyUtil.findElementAtOffset(file, offset);
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection() && selectionModel.getSelectionStart() < offset) {
      PsiElement startElement = PyUtil.findElementAtOffset(file, selectionModel.getSelectionStart());
      if (startElement != null && startElement != endElement && startElement.getTextRange().getEndOffset() == offset) {
        return startElement;
      }
    }
    return endElement;
  }
}
