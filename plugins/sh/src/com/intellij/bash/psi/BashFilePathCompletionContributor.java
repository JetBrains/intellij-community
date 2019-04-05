package com.intellij.bash.psi;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public class BashFilePathCompletionContributor extends CompletionContributor implements DumbAware {
  @Override
  public void duringCompletion(@NotNull CompletionInitializationContext context) {
    Caret caret = context.getCaret();
    Editor editor = context.getEditor();
    context.setReplacementOffset(calcDefaultIdentifierEnd(editor, calcSelectionEnd(caret)));
  }

  private static int calcSelectionEnd(Caret caret) {
    return caret.hasSelection() ? caret.getSelectionEnd() : caret.getOffset();
  }

  private static int calcDefaultIdentifierEnd(Editor editor, int startFrom) {
    final CharSequence text = editor.getDocument().getCharsSequence();
    int idEnd = startFrom;
    int length = text.length();
    while (idEnd < length) {
      char ch = text.charAt(idEnd);
      if (Character.isJavaIdentifierPart(ch) || ch == '.') {
        idEnd++;
      }
      else if (idEnd < length - 1 && ch == '\\' && text.charAt(idEnd + 1) == ' ') {
        idEnd += 2;
      }
      else {
        return idEnd;
      }
    }
    return idEnd;
  }

  public BashFilePathCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inFile(instanceOf(BashFile.class)), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        PsiElement original = parameters.getOriginalPosition();
        String originalText = original == null ? null : original.getText();

        if (originalText != null) {
          originalText = parameters.getOriginalFile().getText().substring(parameters.getPosition().getTextRange().getStartOffset(), parameters.getOffset());
          int lastSlashIndex = originalText.lastIndexOf("/");
          if (lastSlashIndex >= 0) {
            String afterSlash = originalText.substring(lastSlashIndex + 1);
            String beforeSlash = originalText.substring(0, lastSlashIndex);

            boolean isRoot = beforeSlash.isEmpty();

            if (beforeSlash.startsWith("/") || isRoot || beforeSlash.startsWith("~")) {  // absolute paths
              String maybeFilePath = FileUtil.expandUserHome(beforeSlash.replaceAll("\\\\ ", " "));

              File dir = isRoot ? new File("/") : new File(maybeFilePath);
              if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                  List<LookupElement> collect = Arrays.stream(files).map(BashFilePathCompletionContributor::createFileLookupElement).collect(Collectors.toList());
                  result.withPrefixMatcher(afterSlash.replaceAll("\\\\ ", " ")).caseInsensitive().addAllElements(collect);
                }
              }
              result.stopHere();
            }
          }
        }
      }
    });
  }

  private static LookupElement createFileLookupElement(File file) {
    String name = file.getName();
    return LookupElementBuilder.create(name)
        .withInsertHandler((context, item) -> {
          Document document = context.getEditor().getDocument();
          int start = context.getStartOffset();
          int end = context.getSelectionEndOffset();
          boolean alreadyFollowedBySlash = document.getTextLength() < end && document.getCharsSequence().charAt(end) == '/';
          document.deleteString(start, end);
          String newName = name.replaceAll(" ", "\\\\ ") + (file.isDirectory() && !alreadyFollowedBySlash ? "/" : "");
          document.insertString(start, newName);
          int caretOffset = context.getEditor().getCaretModel().getOffset();
          context.getEditor().getCaretModel().moveToOffset(caretOffset + newName.length());
          if (file.isDirectory()) {
            AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
          }
        });
  }
}
