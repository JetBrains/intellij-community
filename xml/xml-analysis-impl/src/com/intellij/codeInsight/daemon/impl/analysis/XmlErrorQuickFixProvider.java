/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XmlErrorQuickFixProvider implements ErrorQuickFixProvider {
  @NonNls private static final String AMP_ENTITY = "&amp;";

  @Override
  public void registerErrorQuickFix(@NotNull final PsiErrorElement element, @NotNull final HighlightInfo highlightInfo) {
    if (PsiTreeUtil.getParentOfType(element, XmlTag.class) != null) {
      registerXmlErrorQuickFix(element,highlightInfo);
    }
  }

  private static void registerXmlErrorQuickFix(final PsiErrorElement element, final HighlightInfo highlightInfo) {
    final String text = element.getErrorDescription();
    if (text != null && text.startsWith(XmlErrorMessages.message("unescaped.ampersand"))) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new IntentionAction() {
        @Override
        @NotNull
        public String getText() {
          return XmlErrorMessages.message("escape.ampersand.quickfix");
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
          if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
          final int textOffset = element.getTextOffset();
          editor.getDocument().replaceString(textOffset,textOffset + 1,AMP_ENTITY);
        }

        @Override
        public boolean startInWriteAction() {
          return true;
        }
      });
    }
  }
}
