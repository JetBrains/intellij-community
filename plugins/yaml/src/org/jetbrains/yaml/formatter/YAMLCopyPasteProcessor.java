// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.*;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;

import java.util.Iterator;
import java.util.List;

public class YAMLCopyPasteProcessor implements CopyPastePreProcessor {
  @Nullable
  @Override
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @NotNull
  @Override
  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (file.getLanguage() != YAMLLanguage.INSTANCE) return text;
    CaretModel caretModel = editor.getCaretModel();
    SelectionModel selectionModel = editor.getSelectionModel();
    Document document = editor.getDocument();
    int caretOffset = selectionModel.getSelectionStart() != selectionModel.getSelectionEnd() ?
                            selectionModel.getSelectionStart() : caretModel.getOffset();
    int lineNumber = document.getLineNumber(caretOffset);
    int lineStartOffset = YAMLTextUtil.getLineStartSafeOffset(document, lineNumber);
    int indent = caretOffset - lineStartOffset;

    String specialKeyPaste = tryToPasteAsKeySequence(text, file, editor, caretOffset, indent);
    if (specialKeyPaste != null) {
      return specialKeyPaste;
    }

    if (indent == 0) {
      // It could be copy and paste of lines
      // User could fix indentation later if he wanted to copy some block into top-level block
      return text;
    }

    return indentText(text, StringUtil.repeatSymbol(' ', indent));
  }

  @NotNull
  private static String indentText(@NotNull String text, @NotNull String curLineIndent) {
    List<String> lines = LineTokenizer.tokenizeIntoList(text, false, false);
    if (lines.isEmpty()) {
      // Such situation should not be possible
      Logger.getInstance(YAMLCopyPasteProcessor.class).error(text.isEmpty()
                                                             ? "Pasted empty text"
                                                             : "Text '" + text + "' was converted into empty line list");
      return text;
    }
    int minIndent = calculateMinBlockIndent(lines);
    String firstLine = lines.iterator().next();
    if (lines.size() == 1) {
      return firstLine;
    }
    return firstLine.substring(YAMLTextUtil.getStartIndentSize(firstLine)) + "\n" +
           lines.stream().skip(1).map(line -> {
             // remove common indent and add needed indent
             if (isEmptyLine(line)) {
               return curLineIndent + line.substring(minIndent);
             }
             else {
               // do not indent empty lines at all
               return "";
             }
           }).reduce((left, right) -> left + "\n" + right).orElse("");
  }

  private static int calculateMinBlockIndent(@NotNull List<String> list) {
    Iterator<String> it = list.iterator();
    String str = "";
    while (it.hasNext()) {
      str = it.next();
      if (isEmptyLine(str)) {
        break;
      }
    }
    if (!it.hasNext()) {
      return 0;
    }
    int minIndent = YAMLTextUtil.getStartIndentSize(str);
    while (it.hasNext()) {
      str = it.next();
      if (isEmptyLine(str)) {
        minIndent = Math.min(minIndent, YAMLTextUtil.getStartIndentSize(str));
      }
    }
    return minIndent;
  }

  private static boolean isEmptyLine(@NotNull String str) {
    return YAMLTextUtil.getStartIndentSize(str) < str.length();
  }

  /** @return text to be pasted or null if it is not possible to paste text as key sequence */
  @Nullable
  private static String tryToPasteAsKeySequence(@NotNull String text,
                                                @NotNull PsiFile file,
                                                @NotNull Editor editor,
                                                int caretOffset,
                                                int indent) {
    List<String> keys = separateCompositeKey(text);
    if (keys == null) {
      return null;
    }
    assert !keys.isEmpty();

    PsiElement element = file.findElementAt(caretOffset);
    if (element != null) {
      String result = tryToPasteAsKeySequenceAtMapping(editor, keys, element, caretOffset, indent);
      if (result != null) {
        return result;
      }
    }

    if ((element == null || element.getTextRange().getStartOffset() == caretOffset) && caretOffset > 0) {
      // check the previous token
      element = file.findElementAt(caretOffset - 1);
      if (element != null) {
        // If we are in the end of map then add element to it:
        // top:
        //   key1: value 1
        //   <caret>
        String result = tryToPasteAsKeySequenceAtMapping(editor, keys, element, caretOffset, indent);
        if (result != null) {
          return result;
        }

        // disputable: insert keys into empty key-value
        // key:
        //   <caret>
        // anotherKey: some value
        YAMLKeyValue keyValue = getPreviousKeyValuePairBeforeEOL(element);
        if (keyValue != null && keyValue.getValue() == null) { // we insert new value
          int parentIndent = YAMLUtil.getIndentToThisElement(keyValue);
          if (indent > parentIndent) {
            return YAMLElementGenerator.createChainedKey(keys, indent);
          }
        }
      }
    }
    return null;
  }

  /** @return found preceding block-style key-value pair after eol or null */
  @Nullable
  private static YAMLKeyValue getPreviousKeyValuePairBeforeEOL(@NotNull PsiElement element) {
    if (PsiUtilCore.getElementType(element.getParent()) != YAMLElementTypes.MAPPING) {
      // TODO: support JSON-like mappings (create minor issue)
      return null;
    }
    boolean eolMet = false;
    PsiElement cur;
    for (cur = element; cur != null; cur = cur.getPrevSibling()) {
      if (!YAMLElementTypes.BLANK_ELEMENTS.contains(PsiUtilCore.getElementType(cur))) {
        break;
      }
      if (PsiUtilCore.getElementType(cur) == YAMLTokenTypes.EOL) {
        eolMet = true;
      }
    }
    if (eolMet && PsiUtilCore.getElementType(cur) == YAMLElementTypes.KEY_VALUE_PAIR) {
      return (YAMLKeyValue)cur;
    }
    return null;
  }

  /** @return text to be pasted or null if it is not possible to paste key sequence */
  @Nullable
  private static String tryToPasteAsKeySequenceAtMapping(@NotNull Editor editor,
                                                         @NotNull List<String> keys,
                                                         @NotNull PsiElement element,
                                                         int caretOffset, int indent) {
    while (true) {
      // TODO: support JSON-like mappings (create minor issue)
      YAMLBlockMappingImpl blockMapping = PsiTreeUtil.getParentOfType(element, YAMLBlockMappingImpl.class);
      if (blockMapping == null) {
        return null;
      }

      int mappingIndent = YAMLUtil.getIndentToThisElement(blockMapping);
      if (mappingIndent == indent) {
        YAMLKeyValue keyValue = ApplicationManager.getApplication().runWriteAction(
          (Computable<YAMLKeyValue>)() -> {
            YAMLKeyValue lastKeyVal = blockMapping.getOrCreateKeySequence(keys, caretOffset);
            if (lastKeyVal == null) {
              return null;
            }
            ASTNode colon = lastKeyVal.getNode().findChildByType(YAMLTokenTypes.COLON);
            if (colon != null) {
              int newOffset = colon.getTextRange().getEndOffset();
              editor.getCaretModel().moveToOffset(newOffset);
            }
            return lastKeyVal;
          }
        );
        if (keyValue != null) {
          return "";
        }
      }
      element = blockMapping;
    }
  }

  /** @return separated key sequence or null if text is not a key sequence */
  @Nullable
  private static List<String> separateCompositeKey(@NotNull String text) {
    if (!text.matches("([\\w$-]+\\.)+[\\w$-]+:?\\s*")) {
      return null;
    }
    text = text.trim();
    text = StringUtil.trimEnd(text, ':');
    return StringUtil.split(text, ".");
  }
}
