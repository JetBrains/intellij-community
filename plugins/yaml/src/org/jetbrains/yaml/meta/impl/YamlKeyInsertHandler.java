/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.meta.model.YamlMetaType.ForcedCompletionPath;
import org.jetbrains.yaml.meta.model.YamlMetaType.YamlInsertionMarkup;

import java.util.Optional;

@ApiStatus.Experimental
public abstract class YamlKeyInsertHandler implements InsertHandler<LookupElement> {
  private final boolean myNeedsSequenceItem;

  /**
   * @param needsSequenceItem when true, the inserted key belongs to the complex instance which is part of the sequence items. In this case
   *                          the complete inserted text should contains a sequence item separator as follows: `- keyName: ...`.
   */
  public YamlKeyInsertHandler(boolean needsSequenceItem) {
    myNeedsSequenceItem = needsSequenceItem;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    if (!needsColon(context)) {
      return;
    }

    final ForcedCompletionPath path = Optional.of(item.getObject())
      .filter(ForcedCompletionPath.class::isInstance)
      .map(ForcedCompletionPath.class::cast)
      .orElse(null);

    final String lookupString;
    if (path != null) {  // deep completion
      lookupString = getReplacement();
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), lookupString);
    }
    else {
      lookupString = item.getLookupString();
    }

    String commonPadding = getIndentation(context, lookupString);

    YamlInsertionMarkup insertionMarkup = computeInsertionMarkup(path != null ? path : ForcedCompletionPath.nullPath());

    commonPadding = insertBeforeItem(context, lookupString, commonPadding, insertionMarkup);
    insertionMarkup.insertStringAndCaret(context.getEditor(), commonPadding);
  }

  @NotNull
  protected abstract YamlInsertionMarkup computeInsertionMarkup(@NotNull ForcedCompletionPath forcedCompletionPath);

  @NotNull
  protected abstract String getReplacement();

  private static boolean needsColon(@NotNull InsertionContext context) {
    String tail = getTailString(context);
    for (int i = 0; i < tail.length(); i++) {
      char next = tail.charAt(i);
      if (next == ':') {
        return false;
      }
      if (isCRLF(next)) {
        return true;
      }
    }
    return true;
  }

  /**
   * There may be two, potentially coexisting cases when we need to insert something before item
   * <ul>
   * <li>a) when the key completion is invoked on the same line as parent key: `parentKey: &lt;caret&gt;`, and we need to insert line
   * break</li>
   * <li>b) when the inserted key is the first key of the complex sequence item, additional sequence mark `- ` should be inserted</li>
   * </ul>
   * <p/>
   * Additionally, in the case a) we need to remove all the trailing spaces after the `parentKey:`
   * <p/>
   *
   * @return actual padding to the start of the inserted key
   */
  protected String insertBeforeItem(@NotNull InsertionContext context, @NotNull String lookupString,
                                    final @NotNull String commonPadding_, @NotNull YamlInsertionMarkup markup) {

    final String COLON = ":";
    final String ITEM_MARK = "- ";

    String sameLineBeforeItem = getHeadStringOnCurrentLine(context, lookupString);

    final StringBuilder toBeInserted = new StringBuilder();
    final StringBuilder toBeRemoved = new StringBuilder();
    final StringBuilder totalPadding = new StringBuilder(commonPadding_);

    if (sameLineBeforeItem.contains(COLON)) {
      String afterColonBeforeItem = StringUtil.substringAfterLast(sameLineBeforeItem, COLON);
      if (StringUtil.isEmptyOrSpaces(afterColonBeforeItem)) {
        toBeRemoved.append(afterColonBeforeItem);
      }

      totalPadding.append(markup.getTabSymbol());
      toBeInserted.append('\n').append(totalPadding);
      sameLineBeforeItem = totalPadding.toString();
    }

    if (myNeedsSequenceItem && !sameLineBeforeItem.endsWith(ITEM_MARK)) {
      toBeInserted.append(ITEM_MARK);
      totalPadding.append(StringUtil.repeat(" ", ITEM_MARK.length()));
    }

    if (toBeInserted.length() != 0) {
      final Editor editor = context.getEditor();

      final int currentOffset = editor.getCaretModel().getOffset();
      final int insertedItemStart = currentOffset - lookupString.length();

      ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        Document document = editor.getDocument();
        document.replaceString(insertedItemStart - toBeRemoved.length(), insertedItemStart, toBeInserted);
        editor.getCaretModel().moveToOffset(currentOffset + toBeInserted.length() - toBeRemoved.length());
        PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
      }));
    }
    return totalPadding.toString();
  }

  @NotNull
  private static String getTailString(@NotNull InsertionContext context) {
    return context.getDocument().getText().substring(context.getTailOffset());
  }

  @NotNull
  private static String getHeadString(@NotNull InsertionContext context, @NotNull String lookupString) {
    return context.getDocument().getText().substring(0, context.getTailOffset() - lookupString.length());
  }

  @NotNull
  private static String getHeadStringOnCurrentLine(InsertionContext context, @NotNull String lookupString) {
    String head = getHeadString(context, lookupString);
    int lineStart = -1;
    for (int idx = head.length() - 1; idx >= 0; idx--) {
      char next = head.charAt(idx);
      if (isCRLF(next)) {
        lineStart = idx;
        break;
      }
    }
    return head.substring(lineStart + 1);
  }

  @NotNull
  private static String getIndentation(InsertionContext context, @NotNull String lookupString) {
    String currentLineHead = getHeadStringOnCurrentLine(context, lookupString);
    String spaces = getLeadingSpacesStartingFrom(currentLineHead, 0);
    int spacesLength = spaces.length();
    if (spacesLength < currentLineHead.length() && currentLineHead.charAt(spacesLength) == '-') {
      //array contents is aligned by the key after '-' not by '-' itself
      spaces += " " + getLeadingSpacesStartingFrom(currentLineHead, spacesLength + "-".length());
    }
    return spaces;
  }

  @NotNull
  private static String getLeadingSpacesStartingFrom(String text, int startIdx) {
    int firstNotSpace = text.length();
    for (int idx = startIdx; idx < text.length(); idx++) {
      char next = text.charAt(idx);
      if (!Character.isWhitespace(next)) {
        firstNotSpace = idx;
        break;
      }
    }
    return text.substring(startIdx, firstNotSpace);
  }

  private static boolean isCRLF(char c) {
    return c == '\n' || c == '\r';
  }
}
