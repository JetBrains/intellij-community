/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.net.HttpProxyConfigurable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.config.SvnConfigureProxiesDialog;
import org.jetbrains.idea.svn.svnkit.SvnKitManager;

import javax.swing.*;

public class NetworkSettingsPanel implements ConfigurableUi<SvnConfiguration> {

  @NotNull private final Project myProject;

  private JPanel myMainPanel;
  private JCheckBox myUseCommonProxy;
  private LinkLabel<Object> myNavigateToCommonProxyLink;

  private JSpinner mySSHConnectionTimeout;
  private JSpinner mySSHReadTimeout;
  private JSpinner myHttpTimeout;

  private JBRadioButton mySSLv3RadioButton;
  private JBRadioButton myTLSv1RadioButton;
  private JBRadioButton myAllRadioButton;
  private JLabel mySSLExplicitly;

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
    if (SvnKitManager.isSSLProtocolExplicitlySet()) {
      mySSLv3RadioButton.setEnabled(false);
      myTLSv1RadioButton.setEnabled(false);
      myAllRadioButton.setEnabled(false);
      mySSLExplicitly.setVisible(true);
      mySSLExplicitly.setText("Set explicitly to: " + SvnKitManager.getExplicitlySetSslProtocols());
    }
    else {
      mySSLv3RadioButton.setEnabled(true);
      myTLSv1RadioButton.setEnabled(true);
      myAllRadioButton.setEnabled(true);
      mySSLExplicitly.setVisible(false);
      final String version = SystemInfo.JAVA_RUNTIME_VERSION;
      final boolean jdkBugFixed = version.startsWith("1.7") || version.startsWith("1.8");
      if (!jdkBugFixed) {
        mySSLExplicitly.setVisible(true);
        mySSLExplicitly.setText("Setting 'All' value in this JDK version (" + version + ") is not recommended.");
      }
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void reset(@NotNull SvnConfiguration configuration) {
    myUseCommonProxy.setSelected(configuration.isIsUseDefaultProxy());
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
    if (configuration.isIsUseDefaultProxy() != myUseCommonProxy.isSelected()) {
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
    configuration.setIsUseDefaultProxy(myUseCommonProxy.isSelected());
    configuration.setSshConnectionTimeout(((SpinnerNumberModel)mySSHConnectionTimeout.getModel()).getNumber().longValue() * 1000);
    configuration.setSshReadTimeout(((SpinnerNumberModel)mySSHReadTimeout.getModel()).getNumber().longValue() * 1000);
    configuration.setHttpTimeout(((SpinnerNumberModel)myHttpTimeout.getModel()).getNumber().longValue() * 1000);
    configuration.setSslProtocols(getSelectedSSL());
    SvnVcs.getInstance(myProject).getSvnKitManager().refreshSSLProperty();
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
