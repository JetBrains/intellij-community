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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ui.OptionsDialog;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public abstract class UpdateOrStatusOptionsDialog extends OptionsDialog {
  private final JComponent myMainPanel;
  private final Map<UpdateEnvironment, Configurable> myEnvToConfMap = new HashMap<UpdateEnvironment, Configurable>();
  protected final Project myProject;


  public UpdateOrStatusOptionsDialog(Project project, Map<Configurable, UpdateEnvironment> confs) {
    super(project);
    setTitle(getRealTitle());
    myProject = project;
    if (confs.size() == 1) {
      myMainPanel = new JPanel(new BorderLayout());
      addComponent(confs.get(confs.keySet().iterator().next()), confs.keySet().iterator().next(), BorderLayout.CENTER);
      myMainPanel.add(new JSeparator(), BorderLayout.SOUTH);
    }
    else {
      myMainPanel = new JTabbedPane();
      for (Iterator<Configurable> iterator = confs.keySet().iterator(); iterator.hasNext();) {
        addComponent(confs.get(iterator.next()), iterator.next(), iterator.next().getDisplayName());
      }
    }
    init();
  }

  protected abstract String getRealTitle();

  private void addComponent(UpdateEnvironment updateEnvironment, Configurable configurable, String constraint) {
    myEnvToConfMap.put(updateEnvironment, configurable);
    myMainPanel.add(configurable.createComponent(), constraint);
    configurable.reset();
  }

  protected void doOKAction() {
    for (Iterator<Configurable> iterator = myEnvToConfMap.values().iterator(); iterator.hasNext();) {
      Configurable configurable = iterator.next();
      try {
        configurable.apply();
      }
      catch (ConfigurationException e) {
        Messages.showErrorDialog(myProject, "Cannot save sattings: " + e.getLocalizedMessage(), getRealTitle());
        return;
      }
    }
    super.doOKAction();
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }

  protected JComponent createCenterPanel() {

    return myMainPanel;
  }
}
