// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.BackspaceHandler;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLFile;

import java.util.Objects;

public class YAMLEnterAtIndentHandler extends EnterHandlerDelegateAdapter {
  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    if (!(file instanceof YAMLFile)) {
      return Result.Continue;
    }

    if (caretOffset.get() > 0 && insertAutomaticHyphen(file)) {
      PsiElement element = file.findElementAt(caretOffset.get() - 1);
      if (PsiUtilCore.getElementType(element) == TokenType.WHITE_SPACE &&
          element.getTextLength() == 1 &&
          PsiUtilCore.getElementType(PsiTreeUtil.prevLeaf(element)) == YAMLTokenTypes.SEQUENCE_MARKER) {
        Document document = editor.getDocument();
        int indentSize = Objects.requireNonNull(CodeStyle.getLanguageSettings(file, YAMLLanguage.INSTANCE).getIndentOptions()).INDENT_SIZE;
        ApplicationManager.getApplication().runWriteAction(() -> {
          document.replaceString(caretOffset.get() - 2, caretOffset.get(), StringUtil.repeat(" ", indentSize));
          PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
          //editor.getCaretModel().moveToOffset(caretOffset.get() - 2);
        });
        //System.err.println("autoinsert");
        return Result.Stop;
      }
    }

    // honor dedent (RUBY-21803)
    // The solutions is copied from com.jetbrains.python.editor.PyEnterAtIndentHandler
    if (BackspaceHandler.isWhitespaceBeforeCaret(editor)) {
      return Result.DefaultSkipIndent;
    }
    return Result.Continue;
  }

  @Override
  public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext) {
    if (!(file instanceof YAMLFile)) {
      return Result.Continue;
    }
    if (!insertAutomaticHyphen(file)) {
      return Result.Continue;
    }

    int caretOffset = editor.getCaretModel().getOffset();

    if (caretOffset <= 0) {
      // Actually it is magic situation
      return Result.Continue;
    }

    PsiElement element = file.findElementAt(caretOffset - 1);
    if (!YAMLElementTypes.BLANK_ELEMENTS.contains(PsiUtilCore.getElementType(element))) {
      return Result.Continue;
    }
    if (PsiUtilCore.getElementType(element.getParent()) != YAMLElementTypes.SEQUENCE) {
      if (PsiUtilCore.getElementType(element.getParent()) != YAMLElementTypes.MAPPING) {
        return Result.Continue;
      }
      PsiElement prevElem = element.getPrevSibling();
      while (YAMLElementTypes.BLANK_ELEMENTS.contains(PsiUtilCore.getElementType(prevElem))) {
        prevElem = prevElem.getPrevSibling();
      }
      if (PsiUtilCore.getElementType(prevElem) != YAMLElementTypes.KEY_VALUE_PAIR) {
        return Result.Continue;
      }
      if (PsiUtilCore.getElementType(prevElem.getLastChild()) != YAMLElementTypes.SEQUENCE) {
        return Result.Continue;
      }
      if (YAMLUtil.getIndentToThisElement(prevElem.getLastChild()) != YAMLUtil.getIndentToThisElement(element.getParent())) {
        return Result.Continue;
      }
    }
    Document document = editor.getDocument();
    ApplicationManager.getApplication().runWriteAction(() -> {
      document.insertString(caretOffset, "- ");
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
      editor.getCaretModel().moveToOffset(caretOffset + 2);
    });

    //System.err.println("autoinsert");
    return Result.Stop;
  }


  private static boolean insertAutomaticHyphen(@NotNull PsiFile file) {
    return CodeStyle.getCustomSettings(file, YAMLCodeStyleSettings.class).AUTOINSERT_SEQUENCE_MARKER;
  }
}
