// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.SmartEnterUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;

public class PyCollectionLiteralFixer extends PyFixer<PySequenceExpression> {
  public PyCollectionLiteralFixer() {
    super(PySequenceExpression.class);
  }

  @Override
  protected boolean isApplicable(@NotNull Editor editor, @NotNull PySequenceExpression element) {
    return true;
  }

  @Override
  protected void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PySequenceExpression collection) {
    int caretOffset = editor.getCaretModel().getOffset();
    PsiElement collectionItemAnchor = collection.getContainingFile().findElementAt(caretOffset);
    assert collectionItemAnchor != null;
    if (collectionItemAnchor instanceof PsiWhiteSpace) {
      collectionItemAnchor = PyPsiUtils.getPrevNonWhitespaceSiblingOnSameLine(collectionItemAnchor);
    }
    if (collectionItemAnchor != null && isMissingColonError(collectionItemAnchor)) {
      collectionItemAnchor = collectionItemAnchor.getPrevSibling();
    }
    // Since surrounding parentheses don't belong to PyTupleExpression expression,
    // whitespace after its last element is a sibling of the tuple itself
    if (collection instanceof PyTupleExpression && collectionItemAnchor == collection) {
      collectionItemAnchor = ArrayUtil.getLastElement(collection.getElements());
    }
    if (collectionItemAnchor == null) {
      return;
    }
    PsiElement collectionItem = PyPsiUtils.getParentRightBefore(collectionItemAnchor, collection);
    if (!(collectionItem instanceof PyExpression)) {
      return;
    }

    Document document = editor.getDocument();
    int collectionItemLastLine = document.getLineNumber(collectionItem.getTextRange().getEndOffset());
    int caretLine = document.getLineNumber(caretOffset);
    if (collectionItemLastLine != caretLine) {
      return;
    }

    PsiElement nextOnSameLine = PyPsiUtils.getNextNonWhitespaceSiblingOnSameLine(collectionItem);
    if (nextOnSameLine != null && isMissingColonError(nextOnSameLine)) {
      nextOnSameLine = PyPsiUtils.getNextNonWhitespaceSiblingOnSameLine(nextOnSameLine);
    }
    if (nextOnSameLine == null || nextOnSameLine instanceof PsiComment) {
      int separatorOffset = collectionItem.getTextRange().getEndOffset();
      PyKeyValueExpression keyValuePair = as(collectionItem, PyKeyValueExpression.class);
      if (collection instanceof PyDictLiteralExpression && keyValuePair == null && !(collectionItem instanceof PyDoubleStarExpression)) {
        document.insertString(separatorOffset, ": ");
        processor.registerUnresolvedError(separatorOffset + 2);
      }
      // Our parser can't handle "'key': ," sequence
      else if (!(keyValuePair != null && keyValuePair.getValue() == null)) {
        document.insertString(separatorOffset, ",");
        editor.getCaretModel().moveToOffset(separatorOffset + 1);
        SmartEnterUtil.plainEnter(editor);
        // The default behavior is to move the caret after the containing statement, unless other caretOffset is specified explicitly
        processor.registerUnresolvedError(editor.getCaretModel().getOffset());
        // Forcibly commit the document to prevent other fixers and enter processors from being run
        PsiDocumentManager.getInstance(collection.getProject()).commitDocument(document);
      }
    }
  }

  private static boolean isMissingColonError(@NotNull PsiElement element) {
    PsiErrorElement errorElement = as(element, PsiErrorElement.class);
    return errorElement != null && PyPsiBundle.message("PARSE.expected.colon").equals(errorElement.getErrorDescription());
  }
}
