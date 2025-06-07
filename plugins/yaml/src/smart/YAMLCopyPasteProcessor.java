// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.smart;

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.*;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl;
import org.jetbrains.yaml.refactoring.rename.YamlKeyValueRenameInputValidator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class YAMLCopyPasteProcessor implements CopyPastePreProcessor {
  private static final String CONFIG_KEY_SEQUENCE_PATTERN = "\\.*([^\\s{}\\[\\].][^\\s.]*\\.)+[^\\s{}\\[\\].][^\\s.]*:?\\s*";

  @Override
  public @Nullable String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    if (startOffsets.length != 1 || endOffsets.length != 1) {
      return null;
    }
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return null;
    }

    int caretOffset = startOffsets[0];
    PsiElement element = file.findElementAt(caretOffset);
    if (element == null) {
      return null;
    }
    PsiElement prevLeaf = PsiTreeUtil.prevLeaf(element, true);
    if (prevLeaf instanceof PsiWhiteSpace) {
      prevLeaf = PsiTreeUtil.prevLeaf(prevLeaf, true);
    }

    if (element instanceof PsiWhiteSpace) {
      element = PsiTreeUtil.nextLeaf(element, true);
      if (element == null) {
        return null;
      }
    }

    boolean inTheStartOfSubElement =
      isYamlElementType(element) && PsiUtilCore.getElementType(element) == YAMLTokenTypes.INDENT
      ||
      element.getTextRange().getStartOffset() == caretOffset &&
      isYamlElementType(prevLeaf) &&
      PsiUtilCore.getElementType(prevLeaf) == YAMLTokenTypes.INDENT
      ||
      element.getTextRange().getStartOffset() >= caretOffset &&
      isYamlElementType(prevLeaf) &&
      PsiUtilCore.getElementType(prevLeaf) == YAMLTokenTypes.SEQUENCE_MARKER;

    if (!inTheStartOfSubElement) {
      return null;
    }

    int lineStartOffset = DocumentUtil.getLineStartOffset(caretOffset, document);

    if (lineStartOffset == caretOffset) {
      return null;
    }

    PsiElement borderParent = PsiTreeUtil.findFirstParent(
      PsiTreeUtil.nextLeaf(element, false), true,
      (psi) -> psi instanceof YAMLBlockSequenceImpl || psi instanceof YAMLBlockMappingImpl
    );

    if (borderParent == null) {
      return null;
    }

    int endOffset = endOffsets[0];
    // one step back from the end of line and from end of file
    PsiElement endElement = file.findElementAt(endOffset - 1);
    if (endElement != null) {
      if (YAMLUtil.psiAreAtTheSameLine(element, endElement)) {
        return null;
      }
    }

    if (borderParent.getTextRange().getEndOffset() < endOffset) {
      PsiElement nextElement = borderParent;
      while (true) {
        nextElement = PsiTreeUtil.nextLeaf(nextElement, true);
        if (nextElement == null || nextElement.getTextRange().getStartOffset() >= endOffset) {
          break;
        }

        IElementType elementType = PsiUtilCore.getElementType(nextElement);
        if (!(elementType instanceof YAMLElementType)) {
          return null;
        }

        if (!YAMLElementTypes.BLANK_ELEMENTS.contains(elementType)) {
          return null;
        }
      }
    }

    return StringUtil.repeatSymbol(' ', caretOffset - lineStartOffset) + text;
  }

  private static boolean isYamlElementType(PsiElement element) {
    return PsiUtilCore.getElementType(element) instanceof YAMLElementType;
  }

  @Override
  public @NotNull String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (!file.getViewProvider().hasLanguage(YAMLLanguage.INSTANCE)) return text;
    CaretModel caretModel = editor.getCaretModel();
    SelectionModel selectionModel = editor.getSelectionModel();
    int caretOffset = selectionModel.getSelectionStart() != selectionModel.getSelectionEnd() ?
                      selectionModel.getSelectionStart() : caretModel.getOffset();
    if (isCommentElement(file.findElementAt(caretOffset))) return text;

    Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(caretOffset);
    int lineStartOffset = YAMLTextUtil.getLineStartSafeOffset(document, lineNumber);
    int indent = caretOffset - lineStartOffset;

    boolean smartPaste = YAMLEditorOptions.getInstance().isUseSmartPaste();
    String specialKeyPaste = smartPaste ? tryToPasteAsKeySequence(text, file, editor, caretOffset, indent) : null;
    if (specialKeyPaste != null) {
      return specialKeyPaste;
    }

    // It could be copy and paste of lines
    // User could fix indentation later if he wanted to copy some block into top-level block
    if (indent == 0) {
      return text;
    }

    LineAdjustmentMode adjustmentMode = adjustmentAfterInsertion(file, caretOffset);
    if (adjustmentMode == LineAdjustmentMode.None) {
      return text;
    }

    if (adjustmentMode == LineAdjustmentMode.Indent) {
      return indentText(text, StringUtil.repeatSymbol(' ', indent), shouldInsertIndentAtTheEnd(caretOffset, document));
    }

    List<String> lines = LineTokenizer.tokenizeIntoList(text, false, false);
    if (lines.isEmpty()) {
      return text;
    }

    String firstLine = lines.get(0);

    // we are inserting a yaml fragment into a value
    // we don't turn the remaining lines into sequence items, just reindent them
    if (firstLine.trim().endsWith(":")) {
      return indentText(text, StringUtil.repeatSymbol(' ', indent), shouldInsertIndentAtTheEnd(caretOffset, document));
    }

    // if we have separate lines pasted into a sequence, turn each line into a sequence item
    return adjustListItems(lines, indent + fixOffset(document, caretOffset));
  }

  private static int fixOffset(Document document, int caretOffset) {
    // the initially computed indent includes everything before the caret,
    // so we have to trim the '-' prefix and following ws
    CharSequence sequence = document.getCharsSequence();
    int offset = 0;
    while (sequence.charAt(caretOffset - 1 + offset) != '-') {
      offset--;
    }
    return offset - 1;
  }

  private static String adjustListItems(List<String> lines, int indent) {
    String firstLine = lines.get(0).substring(YAMLTextUtil.getStartIndentSize(lines.get(0)));
    // if we are pasting a list of sequence items into a sequence,
    //  there is a high probability that we are inlining them into that sequence, so do that
    String firstLineAdjusted = ContainerUtil.and(lines, s -> s.trim().startsWith("-"))
                               ? StringUtil.trimLeading(StringUtil.trimStart(firstLine, "-")) : firstLine;
    return StreamEx.of(lines)
      .skip(1)
      .map(line -> {
        // remove common indent and add needed indent
        if (isEmptyLine(line)) {
          // do not indent empty lines at all
          return "";
        }
        else if (line.trim().startsWith("-")) {
          return StringUtil.repeatSymbol(' ', indent) + StringUtil.trimLeading(line);
        }
        else {
          return StringUtil.repeatSymbol(' ', indent) + "- " + StringUtil.trimLeading(line);
        }
      })
      .prepend(firstLineAdjusted)
      .reduce((left, right) -> left + "\n" + right)
      .orElse("");
  }

  private static boolean isCommentElement(@Nullable PsiElement element) {
    return element != null && isCommentNode(element.getNode());
  }

  private static boolean isCommentNode(@Nullable ASTNode node) {
    return node != null &&
           YAMLTokenTypes.COMMENT == PsiUtilCore.getElementType(node.getElementType() == YAMLTokenTypes.EOL ? node.getTreePrev() : node);
  }

  private static boolean shouldInsertIndentAtTheEnd(int caretOffset, Document document) {
    for (int i = caretOffset; i < document.getTextLength(); i++) {
      char c = document.getCharsSequence().charAt(i);
      if (c == '\n') return false;
      if (!Character.isWhitespace(c)) return true;
    }
    return false; // insert at the end of the document
  }

  private enum LineAdjustmentMode {
    None,
    Indent,
    ListItem
  }

  private static LineAdjustmentMode adjustmentAfterInsertion(PsiFile file, int caretOffset) {
    PsiElement element = file.findElementAt(caretOffset);

    if (element != null) {
      if (PsiUtilCore.getElementType(element) == YAMLTokenTypes.SCALAR_LIST ||
          PsiUtilCore.getElementType(element.getParent()) == YAMLElementTypes.SCALAR_LIST_VALUE) {
        return LineAdjustmentMode.None;
      }
      TokenSet ends = TokenSet.create(YAMLTokenTypes.EOL, YAMLTokenTypes.SCALAR_EOL, YAMLTokenTypes.COMMENT);
      IElementType nextType = PsiUtilCore.getElementType(element.getNextSibling());
      if (PsiUtilCore.getElementType(element) == YAMLTokenTypes.INDENT && (nextType == null || ends.contains(nextType))) {
        return LineAdjustmentMode.Indent;
      }
    }

    PsiElement previousElement;
    if (element == null) {
      if (caretOffset > 0) {
        previousElement = file.findElementAt(caretOffset - 1);
      }
      else {
        return LineAdjustmentMode.Indent;
      }
    }
    else {
      previousElement = PsiTreeUtil.prevLeaf(element, true);
    }
    if (PsiUtilCore.getElementType(previousElement) == TokenType.WHITE_SPACE) previousElement = PsiTreeUtil.prevLeaf(previousElement, true);

    if (PsiUtilCore.getElementType(previousElement) == YAMLTokenTypes.INDENT ||
        PsiUtilCore.getElementType(previousElement) == YAMLTokenTypes.EOL) {
      return LineAdjustmentMode.Indent;
    }

    if (PsiUtilCore.getElementType(previousElement) == YAMLTokenTypes.SEQUENCE_MARKER ||
        PsiUtilCore.getElementType(previousElement) == YAMLTokenTypes.TEXT && previousElement.textMatches("-")) {
      return LineAdjustmentMode.ListItem;
    }

    return LineAdjustmentMode.None;
  }

  private static @NotNull String indentText(@NotNull String text, @NotNull String curLineIndent, boolean shouldInsertIndentInTheEnd) {
    List<String> lines = LineTokenizer.tokenizeIntoList(text, false, false);
    if (lines.isEmpty()) {
      // Such situation sometimes happens but I don't know how it is possible
      return text;
    }
    int minIndent = calculateMinBlockIndent(lines);
    String firstLine = lines.iterator().next();
    if (lines.size() == 1) {
      return firstLine;
    }
    String suffixIndent;
    if (shouldInsertIndentInTheEnd && isEmptyLine(lines.get(lines.size() - 1))) {
      suffixIndent = curLineIndent;
    }
    else {
      suffixIndent = "";
    }
    return firstLine.substring(YAMLTextUtil.getStartIndentSize(firstLine)) + "\n" +
           lines.stream().skip(1).map(line -> {
             // remove common indent and add needed indent
             if (!isEmptyLine(line)) {
               return curLineIndent + line.substring(minIndent);
             }
             else {
               // do not indent empty lines at all
               return "";
             }
           }).reduce((left, right) -> left + "\n" + right).orElse("")
           + suffixIndent;
  }

  private static int calculateMinBlockIndent(@NotNull List<String> list) {
    Iterator<String> it = list.iterator();
    String str = "";
    while (it.hasNext()) {
      str = it.next();
      if (!isEmptyLine(str)) {
        break;
      }
    }
    if (!it.hasNext()) {
      return 0;
    }
    int minIndent = YAMLTextUtil.getStartIndentSize(str);
    while (it.hasNext()) {
      str = it.next();
      if (!isEmptyLine(str)) {
        minIndent = Math.min(minIndent, YAMLTextUtil.getStartIndentSize(str));
      }
    }
    return minIndent;
  }

  private static boolean isEmptyLine(@NotNull String str) {
    return YAMLTextUtil.getStartIndentSize(str) == str.length();
  }

  /** @return text to be pasted or null if it is not possible to paste text as key sequence */
  private static @Nullable String tryToPasteAsKeySequence(@NotNull String text,
                                                          @NotNull PsiFile file,
                                                          @NotNull Editor editor,
                                                          int caretOffset,
                                                          int indent) {
    if (!text.matches(CONFIG_KEY_SEQUENCE_PATTERN)) {
      return null;
    }
    List<String> keys = separateCompositeKey(text);
    assert !keys.isEmpty();

    for (String key : keys) {
      if (!YamlKeyValueRenameInputValidator.IDENTIFIER_PATTERN.matcher(key).matches()) {
        return null;
      }
    }

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
  private static @Nullable YAMLKeyValue getPreviousKeyValuePairBeforeEOL(@NotNull PsiElement element) {
    if (PsiUtilCore.getElementType(element.getParent()) != YAMLElementTypes.MAPPING) {
      // TODO: RUBY-22437 support JSON-like mappings
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
  private static @Nullable String tryToPasteAsKeySequenceAtMapping(@NotNull Editor editor,
                                                                   @NotNull List<String> keys,
                                                                   @NotNull PsiElement element,
                                                                   int caretOffset, int indent) {
    while (true) {
      // TODO: RUBY-22437 support JSON-like mappings
      YAMLBlockMappingImpl blockMapping;
      if (element.getParent() instanceof YAMLFile) {
        // We are at the end of the file
        PsiElement prev = element.getPrevSibling();
        if (!(prev instanceof YAMLDocument)) {
          return null;
        }
        element = ((YAMLDocument)prev).getTopLevelValue();
        if (!(element instanceof YAMLBlockMappingImpl)) {
          return null;
        }
        blockMapping = ((YAMLBlockMappingImpl)element);
      }
      else {
        blockMapping = PsiTreeUtil.getParentOfType(element, YAMLBlockMappingImpl.class);
      }
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
  private static @NotNull List<String> separateCompositeKey(@NotNull String text) {
    text = text.trim();
    text = StringUtil.trimEnd(text, ':');
    int leadingDotsNumber = StringUtil.countChars(text, '.', 0, true);
    String dotPrefix = text.substring(0, leadingDotsNumber);
    text = text.substring(leadingDotsNumber);
    List<String> sequence = new ArrayList<>(StringUtil.split(text, "."));
    if (!dotPrefix.isEmpty() && !sequence.isEmpty()) {
      sequence.set(0, dotPrefix + sequence.get(0));
    }
    return sequence;
  }

  @Override
  public boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
    return false;
  }
}
