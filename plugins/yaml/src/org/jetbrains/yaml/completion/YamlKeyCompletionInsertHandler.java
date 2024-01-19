// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.*;

import java.util.List;

public abstract class YamlKeyCompletionInsertHandler<T extends LookupElement> implements InsertHandler<T> {

  @NotNull
  protected abstract YAMLKeyValue createNewEntry(@NotNull YAMLDocument document, T item,
                                                 @Nullable YAMLKeyValue parent);

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull T item) {
    final PsiElement currentElement = context.getFile().findElementAt(context.getStartOffset());
    assert currentElement != null : "no element at " + context.getStartOffset();

    YAMLKeyValue parent = PsiTreeUtil.getParentOfType(currentElement, YAMLKeyValue.class);
    final YAMLDocument holdingDocument = PsiTreeUtil.getParentOfType(currentElement, YAMLDocument.class);
    assert holdingDocument != null;

    YAMLValue oldValue = (holdingDocument.getTopLevelValue() instanceof YAMLMapping) ?
                         deleteLookupTextAndRetrieveOldValue(context, currentElement) :
                         null; // Inheritors must handle lookup text removal since otherwise holdingDocument may become invalid.
    if (oldValue instanceof YAMLSequence) {
      trimSequenceItemIndents((YAMLSequence)oldValue);
    }

    final YAMLKeyValue created = createNewEntry(holdingDocument, item, parent != null && parent.isValid() ? parent : null);

    YAMLValue createdValue = created.getValue();
    if (createdValue != null) {
      context.getEditor().getCaretModel().moveToOffset(createdValue.getTextRange().getStartOffset() - 1);
    }
    else {
      context.getEditor().getCaretModel().moveToOffset(created.getTextRange().getEndOffset());
    }
    if (oldValue != null) {
      WriteCommandAction.runWriteCommandAction(context.getProject(),
                                               YAMLBundle.message("YamlKeyCompletionInsertHandler.insert.value"),
                                               null,
                                               () -> created.setValue(oldValue));
    }

    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());

    createdValue = created.getValue(); // retrieve restored value
    YAMLSequenceItem valueItem =
      createdValue instanceof YAMLSequence ? ContainerUtil.getFirstItem(((YAMLSequence)createdValue).getItems()) : null;
    if (valueItem != null) {
      context.getEditor().getCaretModel().moveToOffset(valueItem.getTextOffset() + 1);
    }
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
    assert keyValue.getParentMapping() != null || keyValue.getParent() instanceof YAMLCompoundValue;

    Document document = context.getDocument();
    String text = document.getText(TextRange.create(context.getStartOffset(), context.getTailOffset()));
    if (!keyValue.getKeyText().equals(text)) {
      // Insertion was performed at the start of existing keyValue, we should delete only inserted text from the key.
      document.deleteString(context.getStartOffset(), context.getTailOffset());
      context.commitDocument();
      return null;
    }
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
    WriteCommandAction.runWriteCommandAction(context.getProject(),
                                             YAMLBundle.message("YamlKeyCompletionInsertHandler.remove.key"),
                                             null,
                                             () -> {
                                               YAMLMapping parentMapping = keyValue.getParentMapping();
                                               boolean delete = parentMapping.getNode().getChildren(null).length == 1;
                                               parentMapping.deleteKeyValue(keyValue);
                                               if (delete) {
                                                 parentMapping.delete();
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
          if (!hasDocumentSeparatorBefore(offset, sequence)) {
            offset--;
          }
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

  private static boolean hasDocumentSeparatorBefore(int offset, CharSequence sequence) {
    if (offset < 3) return false;
    if (!sequence.subSequence(offset - 3, offset).equals("---")) return false;
    return offset == 3 || sequence.charAt(offset - 4) == '\n';
  }

  public static boolean isCharAtCaret(Editor editor, char ch) {
    final int startOffset = editor.getCaretModel().getOffset();
    final Document document = editor.getDocument();
    return document.getTextLength() > startOffset && document.getCharsSequence().charAt(startOffset) == ch;
  }

  public static void trimSequenceItemIndents(YAMLSequence yamlSequence) {
    List<YAMLSequenceItem> items = yamlSequence.getItems();
    if (items.size() > 1) {
      YAMLElementGenerator elementGenerator = YAMLElementGenerator.getInstance(yamlSequence.getProject());
      for (int i = 1; i < items.size(); i++) {
        PsiElement element = items.get(i).getPrevSibling();
        if (element != null && element.getNode().getElementType() == YAMLTokenTypes.INDENT) {
          PsiElement newIndent = elementGenerator.createIndent(0);
          element.replace(newIndent);
        }
      }
    }
  }
}
