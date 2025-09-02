// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.tools;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class GenerateDTDAction extends BaseCodeInsightAction {
  private static final Logger LOG = Logger.getInstance(GenerateDTDAction.class);
  @Override
  protected @NotNull CodeInsightActionHandler getHandler(){
    return new CodeInsightActionHandler(){
      @Override
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
        final XmlDocument document = findSuitableXmlDocument(psiFile);
        if (document != null) {
          final @NonNls StringBuilder buffer = new StringBuilder();
          buffer.append("<!DOCTYPE ").append(document.getRootTag().getName()).append(" [\n");
          buffer.append(XmlUtil.generateDocumentDTD(document, true));
          buffer.append("]>\n");
          XmlFile tempFile;
            try{
              final XmlProlog prolog = document.getProlog();
              final PsiElement childOfType = PsiTreeUtil.getChildOfType(prolog, XmlProcessingInstruction.class);
              if (childOfType != null) {
                final String text = childOfType.getText();
                buffer.insert(0,text);
                final PsiElement nextSibling = childOfType.getNextSibling();
                if (nextSibling instanceof PsiWhiteSpace) {
                  buffer.insert(text.length(),nextSibling.getText());
                }
              }
              tempFile = (XmlFile)PsiFileFactory.getInstance(psiFile.getProject()).createFileFromText("dummy.xml", buffer.toString());
              prolog.replace(tempFile.getDocument().getProlog());
            }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    };
  }

  private static @Nullable XmlDocument findSuitableXmlDocument(@Nullable PsiFile psiFile) {
    if (psiFile instanceof XmlFile) {
      final XmlDocument document = ((XmlFile)psiFile).getDocument();
      if (document != null && document.getRootTag() != null) {
        return document;
      }
    }
    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    if (event.isFromContextMenu()) {
      Presentation presentation = event.getPresentation();
      presentation.setVisible(presentation.isEnabled());
    }
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile){
    return psiFile.getLanguage() == XMLLanguage.INSTANCE && findSuitableXmlDocument(psiFile) != null;
  }
}
