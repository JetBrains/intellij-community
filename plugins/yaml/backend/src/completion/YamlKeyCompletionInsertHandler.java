// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.*;
import org.jetbrains.yaml.psi.*;

import java.util.List;

public abstract class YamlKeyCompletionInsertHandler<T extends LookupElement> implements InsertHandler<T> {

  protected abstract @NotNull YAMLKeyValue createNewEntry(@NotNull YAMLDocument document, T item,
                                                          @Nullable YAMLKeyValue parent);

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull T item) {
    // keyValue is created by handler, there is no need in inserting completion char
    context.setAddCompletionChar(false);

    int startOffset = context.getStartOffset();
    PsiFile psiFile = context.getFile();
    YamlOffsetContext offsetContext = new YamlOffsetContext(psiFile, startOffset);
    @SuppressWarnings("DataFlowIssue")
    YAMLValue oldValue = (offsetContext.holdingDocument.getTopLevelValue() instanceof YAMLCompoundValue) ?
                         deleteLookupTextAndRetrieveOldValue(context, offsetContext.currentElement) :
                         null; // Inheritors must handle lookup text removal since otherwise holdingDocument may become invalid.
    if (oldValue instanceof YAMLSequence) {
      trimSequenceItemIndents((YAMLSequence)oldValue);
    }

    if (oldValue == null && !offsetContext.holdingDocument.isValid()) {
      offsetContext = new YamlOffsetContext(psiFile, startOffset);
    }
    @SuppressWarnings("DataFlowIssue") final YAMLKeyValue created =
      createNewEntry(offsetContext.holdingDocument, item, offsetContext.getParentIfValid());

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
      EditorModificationUtilEx.insertStringAtCaret(context.getEditor(), " ");
    }
    else {
      context.getEditor().getCaretModel().moveCaretRelatively(1, 0, false, false, true);
    }
  }

  protected @Nullable YAMLValue deleteLookupTextAndRetrieveOldValue(InsertionContext context, @NotNull PsiElement elementAtCaret) {
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
                                             null, () -> {
        PsiElement parent = keyValue.getParent();
        PsiElement substitute = null;
        if (parent instanceof YAMLMapping parentMapping) {
          parentMapping.deleteKeyValue(keyValue);
          ASTNode[] children = parent.getNode().getChildren(null);
          if (children.length == 1 && children[0] instanceof LeafPsiElement) {
            substitute = children[0].getPsi();
          }
          else if (children.length != 0 &&
                   ContainerUtil.find(children, node -> node.getElementType() == YAMLElementTypes.KEY_VALUE_PAIR) == null) {
            List<ASTNode> notSpaces =
              ContainerUtil.filter(children, child -> !YAMLElementTypes.SPACE_ELEMENTS.contains(child.getElementType()));
            if (notSpaces.size() == 1) {
              substitute = notSpaces.get(0).getPsi();
            }
            else {
              String valueText = parent.getText();
              YAMLFile file = YAMLElementGenerator.getInstance(parent.getProject())
                .createDummyYamlWithText(valueText);
              substitute = file.getDocuments().get(0).getTopLevelValue();
              if (substitute == null) {
                PsiElement grandParent = parent.getParent();
                PsiElement copy = parent.copy();
                ASTNode[] copies = copy.getNode().getChildren(null);
                PsiElement anchor = parent.replace(copies[0].getPsi());
                for (int i = 1; i < copies.length; i++) {
                  anchor = grandParent.addAfter(copies[i].getPsi(), anchor);
                }
                return;
              }
            }
          }
        }
        else {
          YAMLUtil.deleteSurroundingWhitespace(keyValue);
          keyValue.delete();
        }
        if (parent.getNode().getChildren(null).length == 0) {
          parent.delete();
        }
        else if (substitute != null) {
          parent.replace(substitute);
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

  private static class YamlOffsetContext {
    final PsiElement currentElement;
    final YAMLKeyValue parent;
    final YAMLDocument holdingDocument;

    YamlOffsetContext(@NotNull PsiFile psiFile, int offset) {
      currentElement = psiFile.findElementAt(offset);
      assert currentElement != null : "no element at " + offset;

      parent = PsiTreeUtil.getParentOfType(currentElement, YAMLKeyValue.class);
      if (currentElement.getParent() instanceof YAMLFile yamlFile) {
        YAMLDocument document = PsiTreeUtil.getPrevSiblingOfType(currentElement, YAMLDocument.class);
        holdingDocument = document == null ? ContainerUtil.getFirstItem(yamlFile.getDocuments()) : document;
      }
      else {
        holdingDocument = PsiTreeUtil.getParentOfType(currentElement, YAMLDocument.class);
      }
      assert holdingDocument != null;
    }

    @Nullable
    YAMLKeyValue getParentIfValid() {
      return parent != null && parent.isValid() ? parent : null;
    }
  }
}
