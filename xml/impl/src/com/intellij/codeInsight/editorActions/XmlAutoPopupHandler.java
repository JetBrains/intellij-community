// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.psi.codeInsight.XmlAutoPopupEnabler;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

public class XmlAutoPopupHandler extends TypedHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(XmlAutoPopupHandler.class);
  @Override
  public @NotNull Result checkAutoPopup(final char charTyped,
                                        final @NotNull Project project,
                                        final @NotNull Editor editor,
                                        final @NotNull PsiFile file) {
    final boolean isXmlLikeFile = XmlGtTypedHandler.fileContainsXmlLanguage(file);
    boolean spaceInTag = isXmlLikeFile && charTyped == ' ';

    if (spaceInTag) {
      spaceInTag = false;
      final PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());

      if (at != null) {
        final PsiElement parent = at.getParent();
        if (parent instanceof XmlTag) {
          spaceInTag = true;
        }
      }
    }

    if ((charTyped == '<' || charTyped == '{' || charTyped == '/' || spaceInTag) && isXmlLikeFile) {
      autoPopupXmlLookup(project, editor);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }

  public static void autoPopupXmlLookup(final Project project, final Editor editor) {
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, file -> {
      int offset = editor.getCaretModel().getOffset();

      PsiElement lastElement = InjectedLanguageUtil.findElementAtNoCommit(file, offset - 1);
      if (lastElement instanceof PsiFile) { //the very end of an injected file
        lastElement = file.findElementAt(offset - 1);
      }
      if (lastElement == null || !lastElement.isValid()) return false;

      if (doCompleteIfNeeded(offset, file, lastElement)) {
        return true;
      }

      FileViewProvider fileViewProvider = file.getViewProvider();
      Language templateDataLanguage;

      final PsiElement parent = lastElement.getParent();
      if (fileViewProvider instanceof TemplateLanguageFileViewProvider &&
          (templateDataLanguage = ((TemplateLanguageFileViewProvider)fileViewProvider).getTemplateDataLanguage()) != parent.getLanguage()) {
        lastElement = fileViewProvider.findElementAt(offset - 1, templateDataLanguage);
        if (lastElement == null || !lastElement.isValid()) return false;
        return doCompleteIfNeeded(offset, file, lastElement);
      }
      return false;
    });
  }

  private static boolean doCompleteIfNeeded(int offset, PsiFile file, PsiElement lastElement) {
    final Ref<Boolean> isRelevantLanguage = new Ref<>();
    final Ref<Boolean> isAnt = new Ref<>();
    String text = lastElement.getText();
    final int len = offset - lastElement.getTextRange().getStartOffset();
    if (len < text.length()) {
      if (len < 0) {
        LOG.error("wrong last element calculated: " + lastElement);
        return false;
      }
      text = text.substring(0, len);
    }
    if (text.endsWith("<") && isLanguageRelevant(lastElement, file, isRelevantLanguage, isAnt) ||
        text.equals(" ") && isLanguageRelevant(lastElement, file, isRelevantLanguage, isAnt) ||
        text.endsWith("${") && isLanguageRelevant(lastElement, file, isRelevantLanguage, isAnt) && isAnt.get().booleanValue() ||
        text.endsWith("@{") && isLanguageRelevant(lastElement, file, isRelevantLanguage, isAnt) && isAnt.get().booleanValue() ||
        text.endsWith("</") && isLanguageRelevant(lastElement, file, isRelevantLanguage, isAnt) ||
        ContainerUtil.exists(XmlAutoPopupEnabler.EP_NAME.getExtensionList(), p -> p.shouldShowPopup(file, offset))) {
      return true;
    }

    return false;
  }

  private static boolean isLanguageRelevant(final PsiElement element,
                                            final PsiFile file,
                                            final Ref<Boolean> isRelevantLanguage,
                                            final Ref<Boolean> isAnt) {
    Boolean isAntFile = isAnt.get();
    if (isAntFile == null) {
      isAntFile = XmlUtil.isAntFile(file);
      isAnt.set(isAntFile);
    }
    Boolean result = isRelevantLanguage.get();
    if (result == null) {
      Language language = element.getLanguage();
      PsiElement parent = element.getParent();
      if (element instanceof PsiWhiteSpace && parent != null) {
        language = parent.getLanguage();
      }
      result = language instanceof XMLLanguage || HtmlUtil.supportsXmlTypedHandlers(file) || isAntFile.booleanValue();
      isRelevantLanguage.set(result);
    }
    return result.booleanValue();
  }
}