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
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.*;

public class SvnConfigureProxiesDialog extends DialogWrapper implements ValidationListener, TestConnectionPerformer {
  private final SvnConfigureProxiesComponent mySystemTab;
  private final SvnConfigureProxiesComponent myUserTab;
  private JPanel myPanel;
  private JTabbedPane myTabbedPane;
  private final GroupsValidator myValidator;
  private final Project myProject;

  public SvnConfigureProxiesDialog(final Project project) {
    super(project, true);
    valid = true;
    myProject = project;

    setTitle(SvnBundle.message("dialog.title.edit.http.proxies.settings"));

    final Ref<SvnServerFileManager> systemManager = new Ref<>();
    final Ref<SvnServerFileManager> userManager = new Ref<>();

    SvnConfiguration.getInstance(project).getServerFilesManagers(systemManager, userManager);

    myValidator = new GroupsValidator(this);
    mySystemTab = new SvnConfigureProxiesComponent(systemManager.get(), myValidator, this);
    myUserTab = new SvnConfigureProxiesComponent(userManager.get(), myValidator, this);

    init();

    mySystemTab.reset();
    myUserTab.reset();
    myValidator.run();
  }

  public void onError(final String text, final JComponent component, final boolean forbidSave) {
    myTabbedPane.setSelectedComponent(component);
    String errorPrefix = myTabbedPane.getTitleAt(myTabbedPane.indexOfComponent(component)) + ": ";

    setOKActionEnabled(! forbidSave);
    setInvalid(errorPrefix + text);
  }

  public void onSuccess() {
    if (isVisible()) {
      setOKActionEnabled(true);
      setInvalid(null);
    }
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  private boolean applyToTab(final SvnConfigureProxiesComponent component) {
    try {
      component.apply();
    } catch (ConfigurationException e) {
      myTabbedPane.setSelectedComponent(component.createComponent());
      setInvalid(e.getMessage());
      return false;
    }
    return true;
  }

  public void doCancelAction() {
    myValidator.stop();
    super.doCancelAction();
  }

  private boolean applyImpl() {
    if (! applyToTab(myUserTab)) {
      return false;
    }
    if (! applyToTab(mySystemTab)) {
      return false;
    }
    return true;
  }

  protected void doOKAction() {
    if (getOKAction().isEnabled()) {
      if(! applyImpl()) {
        return;
      }
      myValidator.stop();
      close(OK_EXIT_CODE);
    }
  }

  public void execute(final String url) {
    Messages.showInfoMessage(myProject, SvnBundle.message("dialog.edit.http.proxies.settings.test.connection.settings.will.be.stored.text"),
                             SvnBundle.message("dialog.edit.http.proxies.settings.test.connection.settings.will.be.stored.title"));
    if(! applyImpl()) {
      return;
    }
    final Ref<Exception> excRef = new Ref<>();
    final ProgressManager pm = ProgressManager.getInstance();
    pm.runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator pi = pm.getProgressIndicator();
        if (pi != null) {
          pi.setText("Connecting to " + url);
        }
        try {
          SvnVcs.getInstance(myProject).getInfo(SvnUtil.createUrl(url), SVNRevision.HEAD);
        }
        catch (SvnBindException e) {
          excRef.set(e);
        }
      }
    }, "Test connection", true, myProject);
    if (! excRef.isNull()) {
      Messages.showErrorDialog(myProject, excRef.get().getMessage(),
                               SvnBundle.message("dialog.edit.http.proxies.settings.test.connection.error.title"));
    } else {
      Messages.showInfoMessage(myProject, SvnBundle.message("dialog.edit.http.proxies.settings.test.connection.succes.text"),
                               SvnBundle.message("dialog.edit.http.proxies.settings.test.connection.succes.title"));
    }
  }

  private boolean valid;

  public void setInvalid(final String text) {
    valid = (text == null) || ("".equals(text.trim()));
    setErrorText(text);
  }

  public boolean enabled() {
    return valid;
  }

  protected JComponent createCenterPanel() {
    myTabbedPane = new JBTabbedPane();
    myTabbedPane.add(myUserTab.createComponent(), SvnBundle.message("dialog.edit.http.proxies.settings.tab.edit.user.file.title"));
    myTabbedPane.add(mySystemTab.createComponent(), SvnBundle.message("dialog.edit.http.proxies.settings.tab.edit.system.file.title"));
    myPanel.add(myTabbedPane, BorderLayout.NORTH);
    return myPanel;
  }
}
