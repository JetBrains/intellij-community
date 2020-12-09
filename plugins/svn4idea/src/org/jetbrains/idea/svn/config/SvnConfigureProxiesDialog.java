// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.ui.Messages.showInfoMessage;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.SYSTEM_CONFIGURATION_PATH;
import static org.jetbrains.idea.svn.config.SvnIniFile.SERVERS_FILE_NAME;

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

    setTitle(message("dialog.title.edit.http.proxies.settings"));

    var systemManager = new ServersFileManager(new SvnIniFile(SYSTEM_CONFIGURATION_PATH.getValue().resolve(SERVERS_FILE_NAME)));
    var userManager = new ServersFileManager(SvnConfiguration.getInstance(project).getServersFile());

    myValidator = new GroupsValidator(this);
    mySystemTab = new SvnConfigureProxiesComponent(systemManager, myValidator, this);
    myUserTab = new SvnConfigureProxiesComponent(userManager, myValidator, this);

    init();

    mySystemTab.reset();
    myUserTab.reset();
    myValidator.run();
  }

  @Override
  public void onError(@NotNull String text, @NotNull JComponent component, boolean forbidSave) {
    myTabbedPane.setSelectedComponent(component);
    String errorPrefix = myTabbedPane.getTitleAt(myTabbedPane.indexOfComponent(component)) + ": ";

    setOKActionEnabled(!forbidSave);
    setInvalid(errorPrefix + text, myTabbedPane);
  }

  @Override
  public void onSuccess() {
    if (isVisible()) {
      setOKActionEnabled(true);
      setInvalid(null, null);
    }
  }

  @Override
  public boolean shouldCloseOnCross() {
    return true;
  }

  private boolean applyToTab(final SvnConfigureProxiesComponent component) {
    try {
      component.apply();
    }
    catch (ConfigurationException e) {
      myTabbedPane.setSelectedComponent(component.createComponent());
      setInvalid(e.getMessage(), myTabbedPane);
      return false;
    }
    return true;
  }

  @Override
  public void doCancelAction() {
    myValidator.stop();
    super.doCancelAction();
  }

  private boolean applyImpl() {
    if (!applyToTab(myUserTab)) {
      return false;
    }
    if (!applyToTab(mySystemTab)) {
      return false;
    }
    return true;
  }

  @Override
  protected void doOKAction() {
    if (getOKAction().isEnabled()) {
      if (!applyImpl()) {
        return;
      }
      myValidator.stop();
      close(OK_EXIT_CODE);
    }
  }

  @Override
  public void execute(final String url) {
    showInfoMessage(myProject, message("dialog.edit.http.proxies.settings.test.connection.settings.will.be.stored.text"),
                    message("dialog.edit.http.proxies.settings.test.connection.settings.will.be.stored.title"));
    if (!applyImpl()) {
      return;
    }
    final Ref<Exception> excRef = new Ref<>();
    final ProgressManager pm = ProgressManager.getInstance();
    pm.runProcessWithProgressSynchronously(() -> {
      final ProgressIndicator pi = pm.getProgressIndicator();
      if (pi != null) {
        pi.setText(message("progress.message.connecting.to.url", url));
      }
      try {
        SvnVcs.getInstance(myProject).getInfo(SvnUtil.createUrl(url), Revision.HEAD);
      }
      catch (SvnBindException e) {
        excRef.set(e);
      }
    }, message("progress.title.test.connection"), true, myProject);
    if (!excRef.isNull()) {
      showErrorDialog(myProject, excRef.get().getMessage(),
                      message("dialog.edit.http.proxies.settings.test.connection.error.title"));
    }
    else {
      showInfoMessage(myProject, message("dialog.edit.http.proxies.settings.test.connection.success.text"),
                      message("dialog.edit.http.proxies.settings.test.connection.success.title"));
    }
  }

  private boolean valid;

  private void setInvalid(@DialogMessage @Nullable String text, @Nullable JComponent component) {
    valid = isEmptyOrSpaces(text);
    setErrorText(text, component);
  }

  @Override
  public boolean enabled() {
    return valid;
  }

  @Override
  protected JComponent createCenterPanel() {
    myTabbedPane = new JBTabbedPane();
    myTabbedPane.add(myUserTab.createComponent(), message("dialog.edit.http.proxies.settings.tab.edit.user.file.title"));
    myTabbedPane.add(mySystemTab.createComponent(), message("dialog.edit.http.proxies.settings.tab.edit.system.file.title"));
    myPanel.add(myTabbedPane, BorderLayout.NORTH);
    return myPanel;
  }
}
