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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectReloadState;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class RestoreUpdateTree implements ProjectComponent, JDOMExternalizable {
  private final Project myProject;

  private UpdateInfo myUpdateInfo;
  @NonNls private static final String UPDATE_INFO = "UpdateInfo";

  public RestoreUpdateTree(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        if (myUpdateInfo != null && !myUpdateInfo.isEmpty() && ProjectReloadState.getInstance(myProject).isAfterAutomaticReload()) {
          ActionInfo actionInfo = myUpdateInfo.getActionInfo();
          if (actionInfo != null) {
            ProjectLevelVcsManagerEx.getInstanceEx(myProject).showUpdateProjectInfo(myUpdateInfo.getFileInformation(),
                                                                                    VcsBundle.message("action.display.name.update"), actionInfo);
            CommittedChangesCache.getInstance(myProject).refreshIncomingChangesAsync();
          }
          myUpdateInfo = null;
        }
        else {
          myUpdateInfo = null;
        }
      }
    });
  }

  public void projectClosed() {

  }

  public String getComponentName() {
    return "RestoreUpdateTree";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    Element child = element.getChild(UPDATE_INFO);
    if (child != null) {
      UpdateInfo updateInfo = new UpdateInfo(myProject);
      updateInfo.readExternal(child);
      myUpdateInfo = updateInfo;
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myUpdateInfo != null) {
      Element child = new Element(UPDATE_INFO);
      element.addContent(child);
      myUpdateInfo.writeExternal(child);
    }
  }


  public static RestoreUpdateTree getInstance(Project project) {
    return project.getComponent(RestoreUpdateTree.class);
  }

  public void registerUpdateInformation(UpdatedFiles updatedFiles, ActionInfo actionInfo) {
    myUpdateInfo = new UpdateInfo(myProject, updatedFiles, actionInfo);
  }
}
