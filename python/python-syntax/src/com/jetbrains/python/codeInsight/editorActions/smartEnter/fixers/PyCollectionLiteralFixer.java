// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyParsingBundle;
import com.jetbrains.python.ast.PyAstDictLiteralExpression;
import com.jetbrains.python.ast.PyAstDoubleStarExpression;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstKeyValueExpression;
import com.jetbrains.python.ast.PyAstSequenceExpression;
import com.jetbrains.python.ast.PyAstTupleExpression;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.SmartEnterUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class PyCollectionLiteralFixer extends PyFixer<PyAstSequenceExpression> {
  public PyCollectionLiteralFixer() {
    super(PyAstSequenceExpression.class);
  }

  @Override
  protected boolean isApplicable(@NotNull Editor editor, @NotNull PyAstSequenceExpression element) {
    return true;
  }

  @Override
  protected void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstSequenceExpression collection) {
    int caretOffset = editor.getCaretModel().getOffset();
    PsiElement collectionItemAnchor = collection.getContainingFile().findElementAt(caretOffset);
    if (collectionItemAnchor == null) {
      return;
    }
    if (collectionItemAnchor instanceof PsiWhiteSpace) {
      collectionItemAnchor = PyPsiUtilsCore.getPrevNonWhitespaceSiblingOnSameLine(collectionItemAnchor);
    }
    if (collectionItemAnchor != null && isMissingColonError(collectionItemAnchor)) {
      collectionItemAnchor = collectionItemAnchor.getPrevSibling();
    }
    // Since surrounding parentheses don't belong to PyAstTupleExpression expression,
    // whitespace after its last element is a sibling of the tuple itself
    if (collection instanceof PyAstTupleExpression && collectionItemAnchor == collection) {
      collectionItemAnchor = ArrayUtil.getLastElement(collection.getElements());
    }
    if (collectionItemAnchor == null) {
      return;
    }
    PsiElement collectionItem = PyPsiUtilsCore.getParentRightBefore(collectionItemAnchor, collection);
    if (!(collectionItem instanceof PyAstExpression)) {
      return;
    }

    Document document = editor.getDocument();
    int collectionItemLastLine = document.getLineNumber(collectionItem.getTextRange().getEndOffset());
    int caretLine = document.getLineNumber(caretOffset);
    if (collectionItemLastLine != caretLine) {
      return;
    }

    PsiElement nextOnSameLine = PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(collectionItem);
    if (nextOnSameLine != null && isMissingColonError(nextOnSameLine)) {
      nextOnSameLine = PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(nextOnSameLine);
    }
    if (nextOnSameLine == null || nextOnSameLine instanceof PsiComment) {
      int separatorOffset = collectionItem.getTextRange().getEndOffset();
      PyAstKeyValueExpression keyValuePair = tryCast(collectionItem, PyAstKeyValueExpression.class);
      if (collection instanceof PyAstDictLiteralExpression && keyValuePair == null && !(collectionItem instanceof PyAstDoubleStarExpression)) {
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
    PsiErrorElement errorElement = tryCast(element, PsiErrorElement.class);
    return errorElement != null && PyParsingBundle.message("PARSE.expected.colon").equals(errorElement.getErrorDescription());
  }
}
