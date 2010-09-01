/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 22.05.2003
 * Time: 13:46:54
 * To change this template use Options | File Templates.
 */
public class GenerateDTDAction extends BaseCodeInsightAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.GenerateDTDAction");
  protected CodeInsightActionHandler getHandler(){
    return new CodeInsightActionHandler(){
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file){
        if(file instanceof XmlFile && file.getVirtualFile() != null && file.getVirtualFile().isWritable()){
          final @NonNls StringBuffer buffer = new StringBuffer();
          final XmlDocument document = ((XmlFile) file).getDocument();
          if(document.getRootTag() != null){
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
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        }
      }

      public boolean startInWriteAction(){
        return true;
      }
    };
  }

  public void update(AnActionEvent event) {
    super.update(event);

    final DataContext dataContext = event.getDataContext();
    final Presentation presentation = event.getPresentation();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    final boolean enabled;
    if (editor != null && project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      enabled = file instanceof XmlFile;
    }
    else {
      enabled = false;
    }

    presentation.setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file){
    return file instanceof XmlFile;
  }
}
