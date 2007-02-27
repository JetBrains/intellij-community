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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

public class VcsGroupsWrapper extends DefaultActionGroup {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.actions.DefaultActionGroup");

  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private AnAction[] myChildren;

  public void update(AnActionEvent e) {
    VcsContext dataContext = VcsContextWrapper.createInstanceOn(e);
    if (myChildren == null) {
      DefaultActionGroup vcsGroupsGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("VcsGroup");
      ArrayList<AnAction> validChildren = new ArrayList<AnAction>();
      AnAction[] children = vcsGroupsGroup.getChildren(new AnActionEvent(null, e.getDataContext(), e.getPlace(), myPresentationFactory.getPresentation(
        vcsGroupsGroup),
                                                                         ActionManager.getInstance(),
                                                                         0));
      for (AnAction child : children) {
        if (!(child instanceof StandardVcsGroup)) {
          LOG.assertTrue(false,
                         "Any version control group should extends com.intellij.openapi.vcs.actions.StandardVcsGroup class. Groupd class: " +
                         child.getClass().getName() + ", group ID: " + ActionManager.getInstance().getId(child));
        }
        else {
          validChildren.add(child);
        }
      }

      myChildren = validChildren.toArray(new AnAction[validChildren.size()]);

    }

    Project project = dataContext.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null) {
      presentation.setVisible(false);
      return;
    }

    Collection<String> currentVcses = new HashSet<String>();

    VirtualFile[] selectedFiles = dataContext.getSelectedFiles();

    ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);

    Map<String, AnAction> vcsToActionMap = new HashMap<String, AnAction>();

    for (AnAction aMyChildren : myChildren) {
      StandardVcsGroup child = (StandardVcsGroup)aMyChildren;
      String vcsName = child.getVcsName(project);
      vcsToActionMap.put(vcsName, child);
    }

    if (selectedFiles != null) {
      for (VirtualFile selectedFile : selectedFiles) {
        AbstractVcs vcs = projectLevelVcsManager.getVcsFor(selectedFile);
        if (vcs != null) {
          currentVcses.add(vcs.getName());
        }
      }
    }


    if (currentVcses.size() == 1 && vcsToActionMap.containsKey(currentVcses.iterator().next())) {
      updateFromAction(vcsToActionMap.get(currentVcses.iterator().next()), presentation);
    }
    else {
      DefaultActionGroup composite = new DefaultActionGroup(VcsBundle.message("group.name.version.control"), true);
      for (AnAction aMyChildren : myChildren) {
        StandardVcsGroup child = (StandardVcsGroup)aMyChildren;
        String vcsName = child.getVcsName(project);
        if (currentVcses.contains(vcsName)) {
          composite.add(child);
        }
      }
      updateFromAction(composite, presentation);

      if (currentVcses.size() == 0) e.getPresentation().setVisible(false);
    }

    super.update(e);
  }

  private void updateFromAction(AnAction action, Presentation presentation) {
    Presentation wrappedActionPresentation = myPresentationFactory.getPresentation(action);
    presentation.setDescription(wrappedActionPresentation.getDescription());
    presentation.restoreTextWithMnemonic(wrappedActionPresentation);
    presentation.setVisible(wrappedActionPresentation.isVisible());
    presentation.setEnabled(wrappedActionPresentation.isEnabled());
    removeAll();
    DefaultActionGroup wrappedGroup = (DefaultActionGroup)action;
    for (AnAction aChildren : wrappedGroup.getChildren(null)) {
      add(aChildren);
    }

  }

}
