package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XmlErrorQuickFixProvider implements ErrorQuickFixProvider {
  @NonNls private static final String AMP_ENTITY = "&amp;";

  public void registerErrorQuickFix(final PsiErrorElement element, final HighlightInfo highlightInfo) {
    if (PsiTreeUtil.getParentOfType(element, XmlTag.class) != null) {
      registerXmlErrorQuickFix(element,highlightInfo);
    }
  }

  private static void registerXmlErrorQuickFix(final PsiErrorElement element, final HighlightInfo highlightInfo) {
    final String text = element.getErrorDescription();
    if (text != null && text.startsWith(XmlErrorMessages.message("unescaped.ampersand"))) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new IntentionAction() {
        @NotNull
        public String getText() {
          return XmlErrorMessages.message("escape.ampersand.quickfix");
        }

        @NotNull
        public String getFamilyName() {
          return getText();
        }

        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return true;
        }

        public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
          if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
          final int textOffset = element.getTextOffset();
          editor.getDocument().replaceString(textOffset,textOffset + 1,AMP_ENTITY);
        }

        public boolean startInWriteAction() {
          return true;
        }
      });
    }
  }
}
