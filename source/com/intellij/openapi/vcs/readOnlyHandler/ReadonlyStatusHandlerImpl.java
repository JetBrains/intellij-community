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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@State(
  name="ReadonlyStatusHandler",
  storages= {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class ReadonlyStatusHandlerImpl extends ReadonlyStatusHandler implements PersistentStateComponent<ReadonlyStatusHandlerImpl.State> {
  private final Project myProject;

  public static class State {
    public boolean SHOW_DIALOG = true;
  }

  private State myState = new State();

  public ReadonlyStatusHandlerImpl(Project project) {
    myProject = project;
  }

  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  public OperationStatus ensureFilesWritable(VirtualFile... files) {
    if (files.length == 0) {
      return new OperationStatus(VirtualFile.EMPTY_ARRAY, VirtualFile.EMPTY_ARRAY);
    }

    ApplicationManager.getApplication().assertIsDispatchThread();

    final long[] modificationStamps = new long[files.length];
    for (int i = 0; i < files.length; i++) {
      modificationStamps[i] = files[i].getModificationStamp();
    }

    final FileInfo[] fileInfos = createFileInfos(files);
    if (fileInfos.length == 0) { // if all files are already writable
      return createResultStatus(files, modificationStamps);
    }
    
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return createResultStatus(files, modificationStamps);
    }

    // This event count hack is necessary to allow actions that called this stuff could still get data from their data contexts.
    // Otherwise data manager stuff will fire up an assertion saying that event count has been changed (due to modal dialog show-up)
    // The hack itself is safe since we guarantee that focus will return to the same component had it before modal dialog have been shown.
    final int savedEventCount = IdeEventQueue.getInstance().getEventCount();
    if (myState.SHOW_DIALOG) {
      new ReadOnlyStatusDialog(myProject, fileInfos).show();
    }
    else {
      processFiles(new ArrayList<FileInfo>(Arrays.asList(fileInfos))); // the collection passed is modified
    }
    IdeEventQueue.getInstance().setEventCount(savedEventCount);
    return createResultStatus(files, modificationStamps);
  }

  private static OperationStatus createResultStatus(final VirtualFile[] files, final long[] modificationStamps) {
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

    return new OperationStatus(
      readOnlyFiles.size() > 0? readOnlyFiles.toArray(new VirtualFile[readOnlyFiles.size()]) : VirtualFile.EMPTY_ARRAY,
      updatedFiles.size() > 0? updatedFiles.toArray(new VirtualFile[updatedFiles.size()]) : VirtualFile.EMPTY_ARRAY
    );
  }

  private FileInfo[] createFileInfos(VirtualFile[] files) {
    List<FileInfo> fileInfos = new ArrayList<FileInfo>();
    for (final VirtualFile file : files) {
      if (file != null && !file.isWritable() && file.isInLocalFileSystem()) {
        fileInfos.add(new FileInfo(file, myProject));
      }
    }
    return fileInfos.toArray(new FileInfo[fileInfos.size()]);
  }

  public static void processFiles(final List<FileInfo> fileInfos) {
    FileInfo[] copy = fileInfos.toArray(new FileInfo[fileInfos.size()]);
    MultiValuesMap<EditFileProvider, VirtualFile> providerToFile = new MultiValuesMap<EditFileProvider, VirtualFile>();
    final List<VirtualFile> unknown = new ArrayList<VirtualFile>();
    for (FileInfo fileInfo : copy) {
      if (fileInfo.getUseVersionControl()) {
        providerToFile.put(fileInfo.getEditFileProvider(), fileInfo.getFile());
      }
      else {
        unknown.add(fileInfo.getFile());
      }
    }

    if (!unknown.isEmpty()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            for (VirtualFile file : unknown) {
              ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false);
              file.refresh(false, false);
            }
          }
          catch (IOException e) {
            //ignore
          }
        }
      });      
    }

    for (EditFileProvider editFileProvider : providerToFile.keySet()) {
      final Collection<VirtualFile> files = providerToFile.get(editFileProvider);
      try {
        editFileProvider.editFiles(files.toArray(new VirtualFile[files.size()]));
      }
      catch (VcsException e) {
        Messages.showErrorDialog(VcsBundle.message("message.text.cannot.edit.file", e.getLocalizedMessage()),
                                 VcsBundle.message("message.title.edit.files"));
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          for (final VirtualFile file : files) {
            file.refresh(false, false);
          }

        }
      });

    }

    for (FileInfo fileInfo : copy) {
      if (!fileInfo.getFile().exists() || fileInfo.getFile().isWritable()) {
        fileInfos.remove(fileInfo);
      }
    }
  }
}
