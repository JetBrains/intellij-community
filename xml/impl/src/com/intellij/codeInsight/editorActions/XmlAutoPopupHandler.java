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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.XmlAutoLookupHandler;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlTag;

public class XmlAutoPopupHandler extends TypedHandlerDelegate {
  public Result checkAutoPopup(final char charTyped, final Project project, final Editor editor, final PsiFile file) {
    final boolean isXmlLikeFile = file.getLanguage() instanceof XMLLanguage || file.getViewProvider().getBaseLanguage() instanceof XMLLanguage;
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

  public static void autoPopupXmlLookup(final Project project, final Editor editor){
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.AUTO_POPUP_XML_LOOKUP) {
      final Runnable request = new Runnable(){
        public void run(){
          PsiDocumentManager.getInstance(project).commitAllDocuments();

          final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
          if (file == null) return;

          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            public void run() {
              new XmlAutoLookupHandler().invoke(project, editor, file);
            }
          }, null, null);
        }
      };
      AutoPopupController.getInstance(project).invokeAutoPopupRunnable(request, settings.XML_LOOKUP_DELAY);
    }
  }

}