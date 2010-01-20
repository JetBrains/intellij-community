package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySliceExpression;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PySliceSelectHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PySliceExpression;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    PySliceExpression slice = (PySliceExpression) e;
    int sliceStart = slice.getLowerBound().getTextOffset();

    int sliceEnd;
    final PyExpression stride = slice.getStride();
    if (stride != null) {
      sliceEnd = stride.getTextRange().getEndOffset();
    }
    else {
      sliceEnd = slice.getUpperBound().getTextRange().getEndOffset();
    }

    if (cursorOffset >= sliceStart && cursorOffset < sliceEnd) {
      return Collections.singletonList(new TextRange(sliceStart, sliceEnd));
    }
    return Collections.emptyList();
  }
}
