package org.jetbrains.yaml.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLValue;

public abstract class YamlKeyCompletionInsertHandler<T extends LookupElement> implements InsertHandler<T> {

  @NotNull
  protected abstract YAMLKeyValue createNewEntry(@NotNull YAMLDocument document, T item);

  @Override
  public void handleInsert(InsertionContext context, T item) {
    final PsiElement currentElement = context.getFile().findElementAt(context.getStartOffset());
    assert currentElement != null : "no element at " + context.getStartOffset();

    final YAMLDocument holdingDocument = PsiTreeUtil.getParentOfType(currentElement, YAMLDocument.class);
    assert holdingDocument != null;

    final YAMLValue oldValue = deleteLookupTextAndRetrieveOldValue(context, currentElement);
    final YAMLKeyValue created = createNewEntry(holdingDocument, item);

    context.getEditor().getCaretModel().moveToOffset(created.getTextRange().getEndOffset());
    if (oldValue != null) {
      WriteCommandAction.runWriteCommandAction(context.getProject(), new Runnable() {
        @Override
        public void run() {
          created.setValue(oldValue);
        }
      });
    }

    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());

    if (!isCharAtCaret(context.getEditor(), ' ')) {
      EditorModificationUtil.insertStringAtCaret(context.getEditor(), " ");
    }
    else {
      context.getEditor().getCaretModel().moveCaretRelatively(1, 0, false, false, true);
    }
  }

  @Nullable
  protected YAMLValue deleteLookupTextAndRetrieveOldValue(InsertionContext context, @NotNull PsiElement elementAtCaret) {
    final YAMLValue oldValue;
    if (elementAtCaret.getNode().getElementType() != YAMLTokenTypes.SCALAR_KEY) {
      deleteLookupPlain(context);
      return null;
    }

    final YAMLKeyValue keyValue = PsiTreeUtil.getParentOfType(elementAtCaret, YAMLKeyValue.class);
    assert keyValue != null;

    context.commitDocument();
    if (keyValue.getValue() != null) {
      // Save old value somewhere
      final YAMLKeyValue dummyKV = YAMLElementGenerator.getInstance(context.getProject()).createYamlKeyValue("foo", "b");
      dummyKV.setValue(keyValue.getValue());
      oldValue = dummyKV.getValue();
    }
    else {
      oldValue = null;
    }

    context.setTailOffset(keyValue.getTextRange().getEndOffset());
    WriteCommandAction.runWriteCommandAction(context.getProject(), new Runnable() {
      @Override
      public void run() {
        keyValue.getParentMapping().deleteKeyValue(keyValue);
      }
    });
    return oldValue;
  }

  private static void deleteLookupPlain(InsertionContext context) {
    final Document document = context.getDocument();
    final CharSequence sequence = document.getCharsSequence();
    int offset = context.getStartOffset() - 1;
    while (offset >= 0) {
      final char c = sequence.charAt(offset);
      if (c != ' ' && c != '\t') {
        if (c == '\n') {
          offset--;
        }
        else {
          offset = context.getStartOffset() - 1;
        }
        break;
      }
      offset--;
    }

    document.deleteString(offset + 1, context.getTailOffset());
    context.commitDocument();
  }

  public static boolean isCharAtCaret(Editor editor, char ch) {
    final int startOffset = editor.getCaretModel().getOffset();
    final Document document = editor.getDocument();
    return document.getTextLength() > startOffset && document.getCharsSequence().charAt(startOffset) == ch;
  }
}
