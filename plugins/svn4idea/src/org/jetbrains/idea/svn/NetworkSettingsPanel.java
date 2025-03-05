// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.net.HttpProxyConfigurable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.config.SvnConfigureProxiesDialog;

import javax.swing.*;

public class NetworkSettingsPanel implements ConfigurableUi<SvnConfiguration> {

  private final @NotNull Project myProject;

  private JPanel myMainPanel;
  private JCheckBox myUseCommonProxy;
  private LinkLabel<Object> myNavigateToCommonProxyLink;

  private JSpinner mySSHConnectionTimeout;
  private JSpinner mySSHReadTimeout;
  private JSpinner myHttpTimeout;

  private JBRadioButton mySSLv3RadioButton;
  private JBRadioButton myTLSv1RadioButton;
  private JBRadioButton myAllRadioButton;

  private JButton myEditProxiesButton;

  public NetworkSettingsPanel(@NotNull Project project) {
    myProject = project;

    myUseCommonProxy.setText(SvnBundle.message("use.idea.proxy.as.default", ApplicationNamesInfo.getInstance().getProductName()));
    myNavigateToCommonProxyLink.setListener((aSource, aLinkData) -> {
      Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(myMainPanel));

      if (settings != null) {
        settings.select(settings.find(HttpProxyConfigurable.class));
      }
    }, null);
    myEditProxiesButton.addActionListener(e -> {
      final SvnConfigureProxiesDialog dialog = new SvnConfigureProxiesDialog(myProject);
      dialog.show();
      myHttpTimeout.setValue(Long.valueOf(SvnConfiguration.getInstance(myProject).getHttpTimeout() / 1000));
    });

    final ButtonGroup bg = new ButtonGroup();
    bg.add(mySSLv3RadioButton);
    bg.add(myTLSv1RadioButton);
    bg.add(myAllRadioButton);
    mySSLv3RadioButton.setEnabled(true);
    myTLSv1RadioButton.setEnabled(true);
    myAllRadioButton.setEnabled(true);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void reset(@NotNull SvnConfiguration configuration) {
    myUseCommonProxy.setSelected(configuration.isUseDefaultProxy());
    mySSHConnectionTimeout.setValue(Long.valueOf(configuration.getSshConnectionTimeout() / 1000));
    mySSHReadTimeout.setValue(Long.valueOf(configuration.getSshReadTimeout() / 1000));
    myHttpTimeout.setValue(Long.valueOf(configuration.getHttpTimeout() / 1000));

    if (SvnConfiguration.SSLProtocols.sslv3.equals(configuration.getSslProtocols())) {
      mySSLv3RadioButton.setSelected(true);
    }
    else if (SvnConfiguration.SSLProtocols.tlsv1.equals(configuration.getSslProtocols())) {
      myTLSv1RadioButton.setSelected(true);
    }
    else {
      myAllRadioButton.setSelected(true);
    }
  }

  @Override
  public boolean isModified(@NotNull SvnConfiguration configuration) {
    if (configuration.isUseDefaultProxy() != myUseCommonProxy.isSelected()) {
      return true;
    }
    if (configuration.getSshConnectionTimeout() / 1000 != ((SpinnerNumberModel)mySSHConnectionTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (configuration.getSshReadTimeout() / 1000 != ((SpinnerNumberModel)mySSHReadTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (configuration.getHttpTimeout() / 1000 != ((SpinnerNumberModel)myHttpTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (!getSelectedSSL().equals(configuration.getSslProtocols())) return true;
    return false;
  }

  @Override
  public void apply(@NotNull SvnConfiguration configuration) {
    configuration.setUseDefaultProxy(myUseCommonProxy.isSelected());
    configuration.setSshConnectionTimeout(((SpinnerNumberModel)mySSHConnectionTimeout.getModel()).getNumber().longValue() * 1000);
    configuration.setSshReadTimeout(((SpinnerNumberModel)mySSHReadTimeout.getModel()).getNumber().longValue() * 1000);
    configuration.setHttpTimeout(((SpinnerNumberModel)myHttpTimeout.getModel()).getNumber().longValue() * 1000);
    configuration.setSslProtocols(getSelectedSSL());
  }

  private SvnConfiguration.SSLProtocols getSelectedSSL() {
    if (myAllRadioButton.isSelected()) return SvnConfiguration.SSLProtocols.all;
    if (mySSLv3RadioButton.isSelected()) return SvnConfiguration.SSLProtocols.sslv3;
    if (myTLSv1RadioButton.isSelected()) return SvnConfiguration.SSLProtocols.tlsv1;
    throw new IllegalStateException();
  }

  private void createUIComponents() {
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);

    myNavigateToCommonProxyLink = new LinkLabel<>(SvnBundle.message("navigate.to.idea.proxy.settings"), null);

    final Long maximum = 30 * 60 * 1000L;
    final long connection = configuration.getSshConnectionTimeout() <= maximum ? configuration.getSshConnectionTimeout() : maximum;
    final long read = configuration.getSshReadTimeout() <= maximum ? configuration.getSshReadTimeout() : maximum;
    mySSHConnectionTimeout =
      createSpinner(new SpinnerNumberModel(Long.valueOf(connection / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
    mySSHReadTimeout = createSpinner(new SpinnerNumberModel(Long.valueOf(read / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
    myHttpTimeout = createSpinner(new SpinnerNumberModel(Long.valueOf(read / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
  }

  private static JSpinner createSpinner(SpinnerModel sm) {
    JSpinner result = new JSpinner(sm);
    JComponent editor = result.getEditor();
    if (UIUtil.isUnderWin10LookAndFeel() && editor instanceof JSpinner.DefaultEditor) {
      ((JSpinner.DefaultEditor)editor).getTextField().setHorizontalAlignment(SwingConstants.RIGHT);
    }
    return result;
  }
}
