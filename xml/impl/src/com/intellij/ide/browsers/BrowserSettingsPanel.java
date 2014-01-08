/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Function;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.LocalPathCellEditor;
import com.intellij.util.ui.table.ComboBoxTableCellEditor;
import com.intellij.util.ui.table.IconTableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily;
import static com.intellij.ide.browsers.TableModelEditor.EditableColumnInfo;

public class BrowserSettingsPanel {
  private JPanel root;

  private JRadioButton useSystemDefaultBrowser;

  private JRadioButton useAlternativeBrowser;
  private TextFieldWithBrowseButton alternativeBrowserPathField;

  private JCheckBox confirmExtractFiles;
  private JButton clearExtractedFiles;
  private JPanel defaultBrowserPanel;

  @SuppressWarnings("UnusedDeclaration")
  private JComponent browsersTable;

  private TableModelEditor<ConfigurableWebBrowser> browsersEditor;

  private final FileChooserDescriptor appFileChooserDescriptor;

  public BrowserSettingsPanel() {
    defaultBrowserPanel.setBorder(IdeBorderFactory.createTitledBorder("Default Browser", true));

    appFileChooserDescriptor = SystemInfo.isMac ?
                               new FileChooserDescriptor(false, true, false, false, false, false) {
                                 @Override
                                 public boolean isFileSelectable(VirtualFile file) {
                                   return file.getName().endsWith(".app");
                                 }
                               } : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    alternativeBrowserPathField.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null,
                                                        appFileChooserDescriptor);

    if (BrowserUtil.canStartDefaultBrowser()) {
      ActionListener actionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateBrowserField();
        }
      };
      useSystemDefaultBrowser.addActionListener(actionListener);
      useAlternativeBrowser.addActionListener(actionListener);
    }
    else {
      useSystemDefaultBrowser.setVisible(false);
      useAlternativeBrowser.setVisible(false);
    }

    clearExtractedFiles.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        BrowserUtil.clearExtractedFiles();
      }
    });
  }

  private void createUIComponents() {
    browsersEditor = new TableModelEditor<ConfigurableWebBrowser>(WebBrowserManager.getInstance().getList(), new ColumnInfo[]{new EditableColumnInfo<ConfigurableWebBrowser, Boolean>() {
      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }

      @Override
      public Boolean valueOf(ConfigurableWebBrowser item) {
        return browsersEditor.getEffective(item).isActive();
      }

      @Override
      public void setValue(ConfigurableWebBrowser item, Boolean value) {
        if (value != item.isActive()) {
          browsersEditor.getMutable(item).setActive(value);
        }
      }
    }, new EditableColumnInfo<ConfigurableWebBrowser, String>("Name") {
      @Override
      public String valueOf(ConfigurableWebBrowser item) {
        return browsersEditor.getEffective(item).getName();
      }

      @Override
      public void setValue(ConfigurableWebBrowser item, String value) {
        if (!value.equals(item.getName())) {
          browsersEditor.getMutable(item).setName(value);
        }
      }
    }, new EditableColumnInfo<ConfigurableWebBrowser, BrowserFamily>("Family") {
      @Override
      public Class getColumnClass() {
        return BrowserFamily.class;
      }

      @Override
      public BrowserFamily valueOf(ConfigurableWebBrowser item) {
        return browsersEditor.getEffective(item).getFamily();
      }

      @Override
      public void setValue(ConfigurableWebBrowser item, BrowserFamily value) {
        if (value != item.getFamily()) {
          browsersEditor.getMutable(item).setFamily(value);
        }
      }

      @Nullable
      @Override
      public TableCellRenderer getRenderer(ConfigurableWebBrowser item) {
        return IconTableCellRenderer.ICONABLE;
      }

      @Nullable
      @Override
      public TableCellEditor getEditor(ConfigurableWebBrowser item) {
        return ComboBoxTableCellEditor.INSTANCE;
      }
    }, new EditableColumnInfo<ConfigurableWebBrowser, String>("Path") {
      @Override
      public String valueOf(ConfigurableWebBrowser info) {
        return browsersEditor.getEffective(info).getPath();
      }

      @Override
      public void setValue(ConfigurableWebBrowser item, String value) {
        String normalizedValue = StringUtil.nullize(value, true);
        if (!Comparing.equal(normalizedValue, item.getPath())) {
          browsersEditor.getMutable(item).setPath(normalizedValue);
        }
      }

      @Nullable
      @Override
      public TableCellEditor getEditor(ConfigurableWebBrowser item) {
        return new LocalPathCellEditor().fileChooserDescriptor(appFileChooserDescriptor);
      }
    }}, new Function<ConfigurableWebBrowser, ConfigurableWebBrowser>() {
      @Override
      public ConfigurableWebBrowser fun(ConfigurableWebBrowser browser) {
        return new ConfigurableWebBrowser(browser.getId(), browser.getFamily(), browser.getName(), browser.getPath(), browser.isActive(), browser.getSpecificSettings());
      }
    }, ConfigurableWebBrowser.class);
    browsersTable = browsersEditor.createComponent();
  }

  @NotNull
  public JPanel getComponent() {
    return root;
  }

  public boolean isModified() {
    GeneralSettings settings = GeneralSettings.getInstance();
    if (!Comparing.strEqual(settings.getBrowserPath(), alternativeBrowserPathField.getText()) ||
        settings.isUseDefaultBrowser() != useSystemDefaultBrowser.isSelected() ||
        settings.isConfirmExtractFiles() != confirmExtractFiles.isSelected()) {
      return true;
    }

    return browsersEditor.isModified(WebBrowserManager.getInstance().getList());
  }

  private void updateBrowserField() {
    if (!BrowserUtil.canStartDefaultBrowser()) {
      return;
    }

    alternativeBrowserPathField.getTextField().setEnabled(useAlternativeBrowser.isSelected());
    alternativeBrowserPathField.getButton().setEnabled(useAlternativeBrowser.isSelected());
  }

  public void apply() throws ConfigurationException {
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setBrowserPath(alternativeBrowserPathField.getText());
    settings.setUseDefaultBrowser(useSystemDefaultBrowser.isSelected());
    settings.setConfirmExtractFiles(confirmExtractFiles.isSelected());

    WebBrowserManager.getInstance().setList(browsersEditor.apply());
  }

  public void reset() {
    GeneralSettings settings = GeneralSettings.getInstance();
    alternativeBrowserPathField.setText(settings.getBrowserPath());

    if (settings.isUseDefaultBrowser()) {
      useSystemDefaultBrowser.setSelected(true);
    }
    else {
      useAlternativeBrowser.setSelected(true);
    }
    confirmExtractFiles.setSelected(settings.isConfirmExtractFiles());

    updateBrowserField();

    browsersEditor.clear();
  }

  public void disposeUIResources() {
  }
}