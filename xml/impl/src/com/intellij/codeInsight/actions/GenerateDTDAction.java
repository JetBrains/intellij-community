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
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.ActionPlaces;
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

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 22.05.2003
 * Time: 13:46:54
 */
public class GenerateDTDAction extends BaseCodeInsightAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.GenerateDTDAction");
  @Override
  @NotNull
  protected CodeInsightActionHandler getHandler(){
    return new CodeInsightActionHandler(){
      @Override
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        final XmlDocument document = findSuitableXmlDocument(file);
        if (document != null) {
          final @NonNls StringBuilder buffer = new StringBuilder();
          buffer.append("<!DOCTYPE " + document.getRootTag().getName() + " [\n");
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
              tempFile = (XmlFile)PsiFileFactory.getInstance(file.getProject()).createFileFromText("dummy.xml", buffer.toString());
              prolog.replace(tempFile.getDocument().getProlog());
            }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }

      @Override
      public boolean startInWriteAction(){
        return true;
      }
    };
  }

  @Nullable
  private static XmlDocument findSuitableXmlDocument(@Nullable PsiFile psiFile) {
    if (psiFile instanceof XmlFile) {
      final XmlDocument document = ((XmlFile)psiFile).getDocument();
      if (document != null && document.getRootTag() != null) {
        return document;
      }
    }
    return null;
  }

  @Override
  public void update(AnActionEvent event) {
    super.update(event);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      Presentation presentation = event.getPresentation();
      presentation.setVisible(presentation.isEnabled());
    }
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file){
    return file.getLanguage() == XMLLanguage.INSTANCE && findSuitableXmlDocument(file) != null;
  }
}
