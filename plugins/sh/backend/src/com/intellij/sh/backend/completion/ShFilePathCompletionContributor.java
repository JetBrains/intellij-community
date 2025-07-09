// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.sh.ShStringUtil;
import com.intellij.sh.ShTypes;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.psi.ShString;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.sh.ShStringUtil.quote;
import static com.intellij.sh.ShStringUtil.unquote;

public class ShFilePathCompletionContributor extends CompletionContributor implements DumbAware {
  private static final InsertHandler<LookupElement> FILE_INSERT_HANDLER = (context, item) -> {
    File file = (File) item.getObject();
    Document document = context.getEditor().getDocument();
    int end = context.getSelectionEndOffset();
    boolean endOfFile = document.getTextLength() == end;
    boolean alreadyFollowedBySlash = end < document.getTextLength() && document.getCharsSequence().charAt(end) == '/';
    boolean needInsertSlash = endOfFile || !alreadyFollowedBySlash;
    if (file.isDirectory()) {
      if (needInsertSlash) {
        document.insertString(end, "/");
      }
      context.getEditor().getCaretModel().moveToOffset(end + 1);
      AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    }
  };

  @Override
  public void duringCompletion(@NotNull CompletionInitializationContext context) {
    super.duringCompletion(context);
    Caret caret = context.getCaret();
    Editor editor = context.getEditor();
    context.setReplacementOffset(calcDefaultIdentifierEnd(editor, calcSelectionEnd(caret)));
  }

  private static int calcSelectionEnd(@NotNull Caret caret) {
    return caret.hasSelection() ? caret.getSelectionEnd() : caret.getOffset();
  }

  // @formatter:off
  private static int calcDefaultIdentifierEnd(@NotNull Editor editor, int startFrom) {
    final CharSequence text = editor.getDocument().getCharsSequence();
    int idEnd = startFrom;
    int length = text.length();
    while (idEnd < length) {
      char ch = text.charAt(idEnd);
      if (Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '-' || ch == '@') idEnd++;
      else if (idEnd < length - 1 && ch == '\\' && ShStringUtil.ORIGINS_SET.contains(text.charAt(idEnd + 1))) idEnd += 2;
      else return idEnd;
    }
    return idEnd;
  }
  // @formatter:on

  public ShFilePathCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inFile(StandardPatterns.instanceOf(ShFile.class)), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        String originalText = getTextWithEnvVarReplacement(parameters);

        if (originalText != null) {
          int lastSlashIndex = originalText.lastIndexOf("/");
          if (lastSlashIndex >= 0) {
            String afterSlash = originalText.substring(lastSlashIndex + 1);
            String beforeSlash = originalText.substring(0, lastSlashIndex);

            boolean isRoot = beforeSlash.isEmpty();

            if (beforeSlash.startsWith("/") || isRoot || beforeSlash.startsWith("~")) {  // absolute paths
              String path = beforeSlash.equals("~") ? "~/" : beforeSlash;
              String maybeFilePath = FileUtil.expandUserHome(unquote(path));
              File dir = isRoot ? new File("/") : new File(maybeFilePath);
              if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                  List<LookupElement> collect = ContainerUtil.map(files, ShFilePathCompletionContributor::createFileLookupElement);
                  String prefix = afterSlash.endsWith("\\") ? afterSlash.substring(0, afterSlash.length() - 1) : afterSlash;
                  result.withPrefixMatcher(prefix).caseInsensitive().addAllElements(collect);
                }
              }
              result.stopHere();
            }
          }
        }
      }
    });
  }

  private static @NotNull LookupElement createFileLookupElement(@NotNull File file) {
    String name = file.getName();
    boolean isDirectory = file.isDirectory();
    return LookupElementBuilder.create(file, quote(name))
        .withIcon(isDirectory ? PlatformIcons.FOLDER_ICON : null)
        .withInsertHandler(FILE_INSERT_HANDLER);
  }

  private static @Nullable String getTextWithEnvVarReplacement(@NotNull CompletionParameters parameters) {
    PsiElement original = parameters.getOriginalPosition();
    if (original == null) return null;

    int textLength = parameters.getOffset() - parameters.getPosition().getTextRange().getStartOffset();
    String originalText = original.getText().substring(0, textLength);
    int offset = original.getTextOffset() - 1;

    if (offset < 0) return originalText;

    PsiElement var = getNearestVarIfExist(parameters.getOriginalFile(), offset);
    if (var == null) return originalText;

    String variable = variableText(var);
    String envPath = EnvironmentUtil.getValue(variable);
    return envPath != null
        ? envPath + originalText
        : originalText;
  }

  private static @Nullable PsiElement getNearestVarIfExist(@NotNull PsiFile file, int offset) {
    PsiElement e = file.findElementAt(offset);
    if (!(e instanceof LeafPsiElement leaf)) return null;
    if (leaf.getElementType() == ShTypes.VAR) return leaf;
    if (isStringOfVar(leaf)) return leaf.getPrevSibling();
    return null;
  }

  // e.g. "$HOME"/<caret>
  private static boolean isStringOfVar(@NotNull LeafPsiElement e) {
    if (e.getElementType() != ShTypes.CLOSE_QUOTE) return false;
    PsiElement str = e.getParent();
    if (!(str instanceof ShString)) return false;

    ASTNode[] children = str.getNode().getChildren(null);
    return children.length == 3 &&
        children[0].getElementType() == ShTypes.OPEN_QUOTE &&
        children[1].getElementType() == ShTypes.VARIABLE &&
        children[2].getElementType() == ShTypes.CLOSE_QUOTE;
  }

  private static @NotNull String variableText(PsiElement e) {
    String variable = e.getText();
    int index = variable.indexOf("$");
    if (index + 1 <= 0) return variable;
    return variable.substring(index + 1);
  }
}