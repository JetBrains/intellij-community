// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XmlErrorQuickFixProvider implements ErrorQuickFixProvider {
  @NonNls private static final String AMP_ENTITY = "&amp;";

  @Override
  public void registerErrorQuickFix(@NotNull final PsiErrorElement element, @NotNull final HighlightInfo.Builder highlightInfo) {
    if (PsiTreeUtil.getParentOfType(element, XmlTag.class) == null) {
      return;
    }
    final String text = element.getErrorDescription();
    if (text.equals(XmlPsiBundle.message("xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"))) {
      final int textOffset = element.getTextOffset();
      highlightInfo.registerFix(new IntentionAction() {
        @Override
        @NotNull
        public String getText() {
          return XmlAnalysisBundle.message("xml.quickfix.escape.ampersand");
        }

        @Override
        @NotNull
        public String getFamilyName() {
          return getText();
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
          PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
          Document document = topLevelFile.getViewProvider().getDocument();
          assert document != null;
          document.replaceString(textOffset, textOffset + 1, AMP_ENTITY);
        }

        @Override
        public boolean startInWriteAction() {
          return true;
        }
      }, null, null, null, null);
    }
  }
}
