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
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

public class ReadonlyStatusHandlerImpl extends ReadonlyStatusHandler implements ProjectComponent, JDOMExternalizable{
  private final Project myProject;

  public boolean SHOW_DIALOG;

  public ReadonlyStatusHandlerImpl(Project project) {
    myProject = project;
  }

  public OperationStatus ensureFilesWritable(VirtualFile[] files) {
    long[] modificationStamps = new long[files.length];
    for (int i = 0; i < files.length; i++) {
      modificationStamps[i] = files[i].getModificationStamp();
    }
    final FileInfo[] fileInfos = createFileInfos(files);
    if (files.length == 0) return new OperationStatus(VirtualFile.EMPTY_ARRAY, VirtualFile.EMPTY_ARRAY);

    Runnable handleAction = new Runnable() {
      public void run() {
        if (SHOW_DIALOG) {
          HandleReadOnlyStatusDialog dialog = new HandleReadOnlyStatusDialog(myProject, fileInfos);
          dialog.show();
        } else {
          for (int i = 0; i < fileInfos.length; i++) {
            fileInfos[i].handle();
          }
        }
      }
    };

    ApplicationManager.getApplication().assertIsDispatchThread();


    // This event count hack is necessary to allow actions that called this stuff could still get data from their data contexts.
    // Otherwise data manager stuff will fire up an assertion saying that event count has been changed (due to modal dialog show-up)
    // The hack itself is safe since we guarantee that focus will return to the same component had it before modal dialog have been shown.
    int savedEventCount = IdeEventQueue.getInstance().getEventCount();
    handleAction.run();
    IdeEventQueue.getInstance().setEventCount(savedEventCount);

    List<VirtualFile> readOnlyFiles = new ArrayList<VirtualFile>();
    List<VirtualFile> updatedFiles = new ArrayList<VirtualFile>();
    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      if (!file.isWritable()) {
        readOnlyFiles.add(file);
      }
      if (modificationStamps[i] != file.getModificationStamp()) {
        updatedFiles.add(file);
      }
    }

    return new OperationStatus(readOnlyFiles.toArray(new VirtualFile[readOnlyFiles.size()]),
                               updatedFiles.toArray(new VirtualFile[updatedFiles.size()]));
  }

  private FileInfo[] createFileInfos(VirtualFile[] files) {
    FileInfo[] result = new FileInfo[files.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new FileInfo(files[i], myProject);      
    }
    return result;
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  public String getComponentName() {
    return "ReadonlyStatusHandler";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this,  element);
  }
}
