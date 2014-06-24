package com.intellij.tasks.youtrack.lang.codeinsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.tasks.youtrack.YouTrackIntellisense;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.intellij.tasks.youtrack.YouTrackIntellisense.CompletionItem;

/**
 * @author Mikhail Golubev
 */
public class YouTrackCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(YouTrackCompletionContributor.class);
  private static final int TIMEOUT = 2000; // ms

  private static final InsertHandler<LookupElement> INSERT_HANDLER = new MyInsertHandler();

  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(DebugUtil.psiToString(parameters.getOriginalFile(), true));
    }

    super.fillCompletionVariants(parameters, result);

    PsiFile file = parameters.getOriginalFile();
    final YouTrackIntellisense intellisense = file.getUserData(YouTrackIntellisense.INTELLISENSE_KEY);
    if (intellisense == null) {
      return;
    }

    final Application application = ApplicationManager.getApplication();
    Future<List<CompletionItem>> future = application.executeOnPooledThread(new Callable<List<CompletionItem>>() {
      @Override
      public List<CompletionItem> call() throws Exception {
        return intellisense.fetchCompletion(parameters.getOriginalFile().getText(), parameters.getOffset());
      }
    });
    try {
      final List<CompletionItem> suggestions = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
      // actually backed by original CompletionResultSet
      result = result.withPrefixMatcher(extractPrefix(parameters)).caseInsensitive();
      result.addAllElements(ContainerUtil.map(suggestions, new Function<CompletionItem, LookupElement>() {
        @Override
        public LookupElement fun(CompletionItem item) {
          return LookupElementBuilder.create(item, item.getOption())
            .withTypeText(item.getDescription(), true)
            .withInsertHandler(INSERT_HANDLER)
            .withBoldness(item.getStyleClass().equals("keyword"));
        }
      }));
    }
    catch (Exception ignored) {
      //noinspection InstanceofCatchParameter
      if (ignored instanceof TimeoutException) {
        LOG.debug(String.format("YouTrack request took more than %d ms to complete", TIMEOUT));
      }
      LOG.debug(ignored);
    }
  }

  /**
   * Find first word left boundary before cursor and strip leading braces and '#' signs
   */
  @NotNull
  private static String extractPrefix(CompletionParameters parameters) {
    String text = parameters.getOriginalFile().getText();
    final int caretOffset = parameters.getOffset();
    if (text.isEmpty() || caretOffset == 0) {
      return "";
    }
    int stopAt = text.lastIndexOf('{', caretOffset - 1);
    // caret isn't inside braces
    if (stopAt <= text.lastIndexOf('}', caretOffset - 1)) {
      // we stay right after colon
      if (text.charAt(caretOffset - 1) == ':') {
        stopAt = caretOffset - 1;
      }
      // use rightmost word boundary as last resort
      else {
        stopAt = text.lastIndexOf(' ', caretOffset - 1);
      }
    }
    //int start = CharArrayUtil.shiftForward(text, lastSpace < 0 ? 0 : lastSpace + 1, "#{ ");
    int prefixStart = stopAt + 1;
    if (prefixStart < caretOffset && text.charAt(prefixStart) == '#') {
      prefixStart++;
    }
    return StringUtil.trimLeading(text.substring(prefixStart, caretOffset));
  }


  /**
   * Inserts additional braces around values that contains spaces, colon after attribute names
   * and '#' before short-cut attributes if any
   */
  private static class MyInsertHandler implements InsertHandler<LookupElement> {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      final CompletionItem completionItem = (CompletionItem)item.getObject();
      final Document document = context.getDocument();
      final Editor editor = context.getEditor();

      context.commitDocument();
      context.setAddCompletionChar(false);

      final String prefix = completionItem.getPrefix();
      final String suffix = completionItem.getSuffix();
      String text = document.getText();
      int offset = context.getStartOffset();
      // skip possible spaces after '{', e.g. "{  My Project <caret>"
      if (prefix.endsWith("{")) {
        while (offset > prefix.length() && Character.isWhitespace(text.charAt(offset - 1))) {
          offset--;
        }
      }
      if (!prefix.isEmpty() && !hasPrefixAt(document.getText(), offset - prefix.length(), prefix)) {
        document.insertString(offset, prefix);
      }
      offset = context.getTailOffset();
      text = document.getText();
      if (suffix.startsWith("} ")) {
        while (offset < text.length() - suffix.length() && Character.isWhitespace(text.charAt(offset))) {
          offset++;
        }
      }
      if (!suffix.isEmpty() && !hasPrefixAt(text, offset, suffix)) {
        document.insertString(offset, suffix);
      }
      editor.getCaretModel().moveToOffset(context.getTailOffset());
    }
  }

  static boolean hasPrefixAt(String text, int offset, String prefix) {
    if (text.isEmpty() || offset < 0 || offset >= text.length()) {
      return false;
    }
    return text.regionMatches(true, offset, prefix, 0, prefix.length());
  }
}


