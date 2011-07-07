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
package com.intellij.ide.browsers;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

/**
 * @author spleaner
 */
public class WebBrowsersPanel extends JPanel {
  private final JPanel mySettingsPanel;
  private Map<BrowsersConfiguration.BrowserFamily, Pair<JCheckBox, TextFieldWithBrowseButton>> myBrowserSettingsMap = new HashMap<BrowsersConfiguration.BrowserFamily, Pair<JCheckBox, TextFieldWithBrowseButton>>();
  private final BrowsersConfiguration myConfiguration;

  public WebBrowsersPanel(final BrowsersConfiguration configuration) {
    setLayout(new BorderLayout());

    myConfiguration = configuration;

    mySettingsPanel = new JPanel();
    mySettingsPanel.setLayout(new BoxLayout(mySettingsPanel, BoxLayout.Y_AXIS));

    add(mySettingsPanel, BorderLayout.NORTH);

    createIndividualSettings(BrowsersConfiguration.BrowserFamily.FIREFOX, mySettingsPanel);
    createIndividualSettings(BrowsersConfiguration.BrowserFamily.EXPLORER, mySettingsPanel);
    createIndividualSettings(BrowsersConfiguration.BrowserFamily.SAFARI, mySettingsPanel);
    createIndividualSettings(BrowsersConfiguration.BrowserFamily.CHROME, mySettingsPanel);
    createIndividualSettings(BrowsersConfiguration.BrowserFamily.OPERA, mySettingsPanel);
  }

  private void createIndividualSettings(@NotNull final BrowsersConfiguration.BrowserFamily family, final JPanel container) {
    final JPanel result = new JPanel();

    result.setBorder(IdeBorderFactory.createTitledBorder(family.getName()));

    result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));

    final TextFieldWithBrowseButton field = new TextFieldWithBrowseButton();
    FileChooserDescriptor descriptor = SystemInfo.isMac
                                       ? FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                       : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    field.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null, descriptor);

    result.add(field);

    final JPanel bottomPanel = new JPanel(new BorderLayout());

    final JPanel activePanel = new JPanel();
    activePanel.setLayout(new BoxLayout(activePanel, BoxLayout.X_AXIS));

    final JCheckBox checkBox = new JCheckBox();
    activePanel.add(checkBox);
    final JLabel label = new JLabel(XmlBundle.message("browser.active"));
    label.setLabelFor(checkBox);
    activePanel.add(label);
    bottomPanel.add(activePanel, BorderLayout.WEST);

    final JButton resetButton = new JButton(XmlBundle.message("browser.default.settings"));
    resetButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        field.getTextField().setText(family.getExecutionPath());
      }
    });

    JPanel buttonsPanel = new JPanel(new BorderLayout());
    if (family.createBrowserSpecificSettings() != null) {
      final JButton editSettingsButton = new JButton(XmlBundle.message("button.text.settings"));
      editSettingsButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editSettings(family);
        }
      });
      buttonsPanel.add(editSettingsButton, BorderLayout.CENTER);
    }
    buttonsPanel.add(resetButton, BorderLayout.EAST);

    bottomPanel.add(buttonsPanel, BorderLayout.EAST);

    result.add(bottomPanel);
    container.add(result);

    final WebBrowserSettings settings = myConfiguration.getBrowserSettings(family);
    field.getTextField().setText(settings.getPath());
    checkBox.setSelected(settings.isActive());

    myBrowserSettingsMap.put(family, Pair.create(checkBox, field));
  }

  private void editSettings(BrowsersConfiguration.BrowserFamily family) {
    BrowserSpecificSettings settings = myConfiguration.getBrowserSettings(family).getBrowserSpecificSettings();
    if (settings == null) {
      settings = family.createBrowserSpecificSettings();
    }

    if (ShowSettingsUtil.getInstance().editConfigurable(mySettingsPanel, settings.createConfigurable())) {
      myConfiguration.updateBrowserSpecificSettings(family, settings);
    }
  }

  public void dispose() {
    myBrowserSettingsMap = null;
  }

  public boolean isModified() {
    for (BrowsersConfiguration.BrowserFamily family : BrowsersConfiguration.BrowserFamily.values()) {
      final WebBrowserSettings old = myConfiguration.getBrowserSettings(family);
      final Pair<JCheckBox, TextFieldWithBrowseButton> settings = myBrowserSettingsMap.get(family);

      if (old.isActive() != settings.first.isSelected() || !old.getPath().equals(settings.second.getText())) {
        return true;
      }
    }

    return false;
  }

  public void apply() {
    for (BrowsersConfiguration.BrowserFamily family : myBrowserSettingsMap.keySet()) {
      final Pair<JCheckBox, TextFieldWithBrowseButton> buttonPair = myBrowserSettingsMap.get(family);
      myConfiguration.updateBrowserValue(family, buttonPair.second.getText(), buttonPair.first.isSelected());
    }
  }

  public void reset() {
    for (BrowsersConfiguration.BrowserFamily family : myBrowserSettingsMap.keySet()) {
      final Pair<JCheckBox, TextFieldWithBrowseButton> buttonPair = myBrowserSettingsMap.get(family);
      final WebBrowserSettings settings = myConfiguration.getBrowserSettings(family);
      buttonPair.first.setSelected(settings.isActive());
      buttonPair.second.getTextField().setText(settings.getPath());
    }
  }
}
