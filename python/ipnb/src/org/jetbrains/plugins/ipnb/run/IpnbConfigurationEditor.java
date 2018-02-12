package org.jetbrains.plugins.ipnb.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class IpnbConfigurationEditor extends SettingsEditor<IpnbRunConfiguration> implements PanelWithAnchor {
  public static final String DEFAULT_HOST = "127.0.0.1";
  public static final String DEFAULT_PORT = "8888";

  private JPanel myPanel;
  private JTextField myAdditionalOptionsTextField;

  private JPanel myCommonOptionsFormPlaceholder;
  private JBLabel myAdditionalOptionsLabel;
  private JTextField myHostTextField;
  private JTextField myPortTextField;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private JComponent myAnchor;

  public IpnbConfigurationEditor(IpnbRunConfiguration configuration) {
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsFormPlaceholder.add(myCommonOptionsForm.getMainPanel(), BorderLayout.CENTER);

    final String title = "Select Configuration File:";
    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory
      .createSingleFileOrFolderDescriptor();
    fileChooserDescriptor.setTitle(title);
  }

  protected void resetEditorFrom(@NotNull IpnbRunConfiguration s) {
    AbstractPythonRunConfiguration.copyParams(s, myCommonOptionsForm);
    setHost(s.getHost());
    setPort(s.getPort());
    myAdditionalOptionsTextField.setText(s.getAdditionalOptions());
  }

  protected void applyEditorTo(@NotNull IpnbRunConfiguration s) throws ConfigurationException {
    AbstractPythonRunConfiguration.copyParams(myCommonOptionsForm, s);

    s.setHost(getHost());
    s.setPort(getPort());
    s.setAdditionalOptions(myAdditionalOptionsTextField.getText());
  }

  @Nullable
  public String getHost() {
    return myHostTextField.getText();
  }

  @Nullable
  public String getPort() {
    return myPortTextField.getText();
  }

  public void setHost(String host) {
    myHostTextField.setText(StringUtil.isEmptyOrSpaces(host) ? DEFAULT_HOST : host);
  }

  public void setPort(String port) {
    myPortTextField.setText(StringUtil.isEmptyOrSpaces(port) ? DEFAULT_PORT : port);
  }

  @NotNull
  protected JComponent createEditor() {
    return myPanel;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.myAnchor = anchor;
    myCommonOptionsForm.setAnchor(anchor);
    myAdditionalOptionsLabel.setAnchor(anchor);
  }
}
