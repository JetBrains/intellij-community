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
package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.OutputStream;

public class DiffRequestFactoryImpl implements DiffRequestFactory {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.mergeTool.DiffRequestFactoryImpl");

  public MergeRequest createMergeRequest(String leftText,
                                         String rightText,
                                         String originalContent,
                                         VirtualFile file,
                                         Project project,
                                         final ActionButtonPresentation actionButtonPresentation) {
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    return new MergeRequestImpl(leftText, createMergeVersion(file, document, originalContent), rightText, project, actionButtonPresentation);
  }

  private MergeVersion createMergeVersion(VirtualFile file,
                                          final Document document,
                                          final String centerText) {
    if (document != null) {
      return new MergeVersion.MergeDocumentVersion(document, centerText);
    }
    else {
      return new MyMergeVersion(centerText, file);
    }
  }

  private class MyMergeVersion implements MergeVersion {
    private final String myCenterText;
    private final VirtualFile myFile;

    public MyMergeVersion(String centerText, VirtualFile file) {
      myCenterText = centerText;
      myFile = file;
    }

    public FileType getContentType() {
      return FileTypeManager.getInstance().getFileTypeByFile(myFile);
    }

    public byte[] getBytes() throws IOException {
      return myCenterText.getBytes(myFile.getCharset().name());
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public Document createWorkingDocument(Project project) {
      return EditorFactory.getInstance().createDocument(myCenterText);
    }


    public void applyText(final String text, final Project project) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            public void run() {
              storeChangedContent(text);
            }

          }, "Writing to File", null);
        }
      });
    }

    private void storeChangedContent(String text) {
      OutputStream outputStream = null;
      byte[] bytes = null;
      try {
        bytes = text.getBytes(myFile.getCharset().name());
        outputStream = myFile.getOutputStream(this);
      }
      catch (IOException e) {
        LOG.error(e);
      }
      try {
        outputStream.write(bytes);
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        try {
          outputStream.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

  }


}
