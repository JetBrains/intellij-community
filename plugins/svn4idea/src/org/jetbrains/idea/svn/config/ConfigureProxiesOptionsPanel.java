// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.InsertPathAction;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  private JList myRepositoriesList;
  private boolean myIsDefault;

  private final Runnable myValidator;
  // listens for patterns+exceptions changes on lost focus
  private PatternsListener myPatternsListener;

  private final Map<JComponent, String> myComponent2Key;
  private final Map<String, JComponent> myKey2Component;

  private final TestConnectionPerformer myTestConnectionPerformer;

  /**
   * called on after repositories list had been recalculated by {@link org.jetbrains.idea.svn.config.PatternsListener}
   *
   * @see org.jetbrains.idea.svn.config.RepositoryUrlsListener#onListChanged(java.util.List)
   */
  @Override
  public void onListChanged(final List<String> urls) {
    final String value = (String) myRepositoriesList.getSelectedValue();
    myRepositoriesList.removeAll();
    myRepositoriesList.setListData(urls.toArray());
    // for keeping selection
    if (value != null) {
      final ListModel model = myRepositoriesList.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        final String element = (String) model.getElementAt(i);
        if (value.equals(element)) {
          myRepositoriesList.setSelectedIndex(i);
        }
      }
    }
    // in order to check for groups ambiguity + maybe highlight another error
    myValidator.run();
  }

  public List<String> getRepositories() {
    final ListModel model = myRepositoriesList.getModel();
    final List<String> result = new ArrayList<>(model.getSize());
    for (int i = 0; i < model.getSize(); i++) {
      result.add((String) model.getElementAt(i));
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
      final String value = (String)myRepositoriesList.getSelectedValue();
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
        SvnBundle.message("dialog.edit.http.proxies.settings.dialog.select.ssl.client.certificate.path.title"),
        null, null, new FileChooserDescriptor(true, false, false, false, false, false));
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
    addToKeyMappings(myServerField, SvnServerFileKeys.SERVER);
    addToKeyMappings(myUserField, SvnServerFileKeys.USER);
    addToKeyMappings(myPortField, SvnServerFileKeys.PORT);
    addToKeyMappings(myPasswordField, SvnServerFileKeys.PASSWORD);
    addToKeyMappings(myExceptions, SvnServerFileKeys.EXCEPTIONS);
    addToKeyMappings(myTimeoutField, SvnServerFileKeys.TIMEOUT);
    addToKeyMappings(myTrustDefaultCAsCheckBox, SvnServerFileKeys.SSL_TRUST_DEFAULT_CA);
    addToKeyMappings(myClientCertificatePasswordField, SvnServerFileKeys.SSL_CLIENT_CERT_PASSWORD);
    addToKeyMappings(myPathToCertificatesField, SvnServerFileKeys.SSL_AUTHORITY_FILES);
    addToKeyMappings(myClientCertificatePathField, SvnServerFileKeys.SSL_CLIENT_CERT_FILE);
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
        JTextComponent textComponent = null;
        if (component instanceof JTextComponent) {
          textComponent = (JTextComponent) component;
        } else if (component instanceof TextFieldWithBrowseButton) {
          textComponent = ((TextFieldWithBrowseButton) component).getTextField();
        }
        if (textComponent != null) {
          textComponent.setText(entry.getValue());
          textComponent.selectAll();
        }
        component.setToolTipText(entry.getKey());
      }
    }

    myTrustDefaultCAsCheckBox.setSelected(booleanPropertySelected(properties.get(myComponent2Key.get(myTrustDefaultCAsCheckBox))));
    repositoryUrlsRecalculation();
  }

  public void copyStringProperties(Map<String, String> map) {
    for (Map.Entry<String, JComponent> entry : myKey2Component.entrySet()) {
      final JComponent component = entry.getValue();
      String value = null;
      if (component instanceof JTextComponent) {
        value = ((JTextComponent) component).getText();
      } else if (component instanceof TextFieldWithBrowseButton) {
        value = ((TextFieldWithBrowseButton) component).getTextField().getText();
      } else if (component instanceof JCheckBox) {
        value = ((JCheckBox) component).isSelected() ? "yes" : "no";
      }

      if ((value != null) && ((! "".equals(value)) || (map.containsKey(entry.getKey())))) {
        map.put(entry.getKey(), value);
      }
    }
  }

  private static boolean booleanPropertySelected(final String value) {
    return value != null && SvnServerFileKeys.YES_OPTIONS.contains(StringUtil.toLowerCase(value));
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
