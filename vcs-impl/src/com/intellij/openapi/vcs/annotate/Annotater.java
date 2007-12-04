/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nullable;

public class Annotater {

  private final Project myProject;
  private final VirtualFile myVirtualFile;
  private final FileAnnotation myFileAnnotation;

  public Annotater(FileAnnotation fileAnnotation, Project project, VirtualFile virtualFile) {
    myFileAnnotation = fileAnnotation;
    myProject = project;
    myVirtualFile = virtualFile;
  }

  public void showAnnotation() {
    OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(myProject, myVirtualFile);
    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(openFileDescriptor, true);
    if (editor == null) {
      Messages.showMessageDialog(VcsBundle.message("message.text.cannot.open.editor", myVirtualFile.getPresentableUrl()),
                                 VcsBundle.message("message.title.cannot.open.editor"), Messages.getInformationIcon());
      return;
    }

    EditorGutter gutterComponent = editor.getGutter();
    gutterComponent.closeAllAnnotations();

    final LineAnnotationAspect[] aspects = myFileAnnotation.getAspects();
    for (int i = 0; i < aspects.length; i++) {
      final LineAnnotationAspect aspect = aspects[i];
      final TextAnnotationGutterProvider provider = new TextAnnotationGutterProvider() {
        public String getLineText(int line, Editor editor) {
          return aspect.getValue(line);
        }

        @Nullable
        public String getToolTip(final int line, final Editor editor) {
          return XmlStringUtil.escapeString(myFileAnnotation.getToolTip(line));
        }

        public void gutterClosed() {
        }
      };
      if (aspect instanceof EditorGutterAction) {
        gutterComponent.registerTextAnnotation(provider, (EditorGutterAction)aspect);
      } else {
       gutterComponent.registerTextAnnotation(provider);
      }


    }
  }
}
