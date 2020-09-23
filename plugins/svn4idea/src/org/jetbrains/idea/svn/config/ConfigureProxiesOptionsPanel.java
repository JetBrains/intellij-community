// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.InsertPathAction;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.config.SvnIniFile.isTurned;

public class ConfigureProxiesOptionsPanel implements RepositoryUrlsListener {
  private JPanel myMainPanel;
  private JTextField myServerField;
  private JTextField myUserField;
  private JTextField myPortField;
  private JPasswordField myPasswordField;
  private JTextArea myUrlPatterns;
  private JTextArea myExceptions;
  private JTextField myTimeoutField;
  private JCheckBox myTrustDefaultCAsCheckBox;
  private JPasswordField myClientCertificatePasswordField;
  private JButton myTestConnectionButton;
  private JTextField myPathToCertificatesField;
  private TextFieldWithBrowseButton myClientCertificatePathField;
  private JList<String> myRepositoriesList;
  private boolean myIsDefault;

  private final Runnable myValidator;
  // listens for patterns+exceptions changes on lost focus
  private PatternsListener myPatternsListener;

  private final Map<JComponent, String> myComponent2Key;
  private final Map<String, JComponent> myKey2Component;

  private final TestConnectionPerformer myTestConnectionPerformer;

  /**
   * called on after repositories list had been recalculated by {@link PatternsListener}
   *
   * @see RepositoryUrlsListener#onListChanged(List)
   */
  @Override
  public void onListChanged(final List<String> urls) {
    final String value = myRepositoriesList.getSelectedValue();
    myRepositoriesList.removeAll();
    myRepositoriesList.setListData(ArrayUtil.toStringArray(urls));
    // for keeping selection
    if (value != null) {
      final ListModel<String> model = myRepositoriesList.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        final String element = model.getElementAt(i);
        if (value.equals(element)) {
          myRepositoriesList.setSelectedIndex(i);
        }
      }
    }
    // in order to check for groups ambiguity + maybe highlight another error
    myValidator.run();
  }

  public List<String> getRepositories() {
    final ListModel<String> model = myRepositoriesList.getModel();
    final List<String> result = new ArrayList<>(model.getSize());
    for (int i = 0; i < model.getSize(); i++) {
      result.add(model.getElementAt(i));
    }
    return result;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void setPatternsListener(final PatternsListener listener) {
    myPatternsListener = listener;
  }

  public ConfigureProxiesOptionsPanel(final Runnable validator, final TestConnectionPerformer testConnectionPerformer) {
    myValidator = validator;
    myTestConnectionPerformer = testConnectionPerformer;

    myComponent2Key = new HashMap<>();
    myKey2Component = new HashMap<>();
    fillMappings();

    initNumericValidation();
    initBrowseActions();
    initRepositories();
    putPatternsListener();

    myTestConnectionButton.addActionListener(e -> {
      final String value = myRepositoriesList.getSelectedValue();
      if ((value != null) && (myTestConnectionPerformer.enabled())) {
        myTestConnectionPerformer.execute(value);
      }
    });
  }

  private void putPatternsListener() {
    final UrlsSetter urlsSetter = new UrlsSetter();
    myUrlPatterns.addFocusListener(urlsSetter);
    myExceptions.addFocusListener(urlsSetter);
  }

  private void initBrowseActions() {
    InsertPathAction.addTo(myPathToCertificatesField, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    myClientCertificatePathField.addBrowseFolderListener(
      message("dialog.edit.http.proxies.settings.dialog.select.ssl.client.certificate.path.title"),
      null, null, new FileChooserDescriptor(true, false, false, false, false, false)
    );
  }

  private void initNumericValidation() {
    final NumericFieldsValidator numericFocusListener = new NumericFieldsValidator();
    myPortField.addFocusListener(numericFocusListener);
    myTimeoutField.addFocusListener(numericFocusListener);
  }

  private void initRepositories() {
    final ListSelectionModel selectionModel = new DefaultListSelectionModel();
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myRepositoriesList.setSelectionModel(selectionModel);
    myRepositoriesList.addListSelectionListener(
      e -> myTestConnectionButton.setEnabled(myTestConnectionPerformer.enabled() && (myRepositoriesList.getSelectedValue() != null)));
  }

  private void fillMappings() {
    addToKeyMappings(myServerField, ServersFileKeys.SERVER);
    addToKeyMappings(myUserField, ServersFileKeys.USER);
    addToKeyMappings(myPortField, ServersFileKeys.PORT);
    addToKeyMappings(myPasswordField, ServersFileKeys.PASSWORD);
    addToKeyMappings(myExceptions, ServersFileKeys.EXCEPTIONS);
    addToKeyMappings(myTimeoutField, ServersFileKeys.TIMEOUT);
    addToKeyMappings(myTrustDefaultCAsCheckBox, ServersFileKeys.SSL_TRUST_DEFAULT_CA);
    addToKeyMappings(myClientCertificatePasswordField, ServersFileKeys.SSL_CLIENT_CERT_PASSWORD);
    addToKeyMappings(myPathToCertificatesField, ServersFileKeys.SSL_AUTHORITY_FILES);
    addToKeyMappings(myClientCertificatePathField, ServersFileKeys.SSL_CLIENT_CERT_FILE);
  }

  private void addToKeyMappings(final JComponent component, final String key) {
    myComponent2Key.put(component, key);
    myKey2Component.put(key, component);
  }

  private class UrlsSetter implements FocusListener {
    @Override
    public void focusGained(final FocusEvent e) {
    }

    @Override
    public void focusLost(final FocusEvent e) {
      repositoryUrlsRecalculation();
    }
  }

  private void repositoryUrlsRecalculation() {
    if (! myIsDefault) {
      myPatternsListener.onChange(myUrlPatterns.getText(), myExceptions.getText());
    }
  }

  public void setIsValid(final boolean valid) {
    myTestConnectionButton.setEnabled(valid && (myRepositoriesList.getSelectedValue() != null));
  }

  private class NumericFieldsValidator implements FocusListener {
    @Override
    public void focusGained(final FocusEvent e) {
    }

    @Override
    public void focusLost(final FocusEvent e) {
      myValidator.run();
    }
  }

  public void setStringProperties(final Map<String, String> properties) {
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      final JComponent component = myKey2Component.get(entry.getKey());
      if (component != null) {
        setProperty(component, entry.getKey(), entry.getValue());
      }
    }

    myTrustDefaultCAsCheckBox.setSelected(isTurned(properties.get(myComponent2Key.get(myTrustDefaultCAsCheckBox)), false));
    repositoryUrlsRecalculation();
  }

  private static void setProperty(@NotNull JComponent component, @NlsSafe String name, @NlsSafe String value) {
    JTextComponent textComponent = null;
    if (component instanceof JTextComponent) {
      textComponent = (JTextComponent)component;
    }
    else if (component instanceof TextFieldWithBrowseButton) {
      textComponent = ((TextFieldWithBrowseButton)component).getTextField();
    }
    if (textComponent != null) {
      textComponent.setText(value);
      textComponent.selectAll();
    }
    component.setToolTipText(name);
  }

  public void copyStringProperties(Map<String, String> map) {
    for (Map.Entry<String, JComponent> entry : myKey2Component.entrySet()) {
      final JComponent component = entry.getValue();
      String value = null;
      if (component instanceof JTextComponent) {
        value = ((JTextComponent)component).getText();
      }
      else if (component instanceof TextFieldWithBrowseButton) {
        value = ((TextFieldWithBrowseButton)component).getTextField().getText();
      } else if (component instanceof JCheckBox) {
        value = ((JCheckBox) component).isSelected() ? "yes" : "no";
      }

      if ((value != null) && ((! "".equals(value)) || (map.containsKey(entry.getKey())))) {
        map.put(entry.getKey(), value);
      }
    }
  }

  public boolean isDefault() {
    return myIsDefault;
  }

  public void setIsDefaultGroup(final boolean value) {
    myIsDefault = value;
    myUrlPatterns.setEditable(! myIsDefault);
  }

  public String getPatterns() {
    return myUrlPatterns.getText();
  }

  public void setPatterns(final String value) {
    myUrlPatterns.setText(value);
    repositoryUrlsRecalculation();
  }
}
