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
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.Function;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.LocalPathCellEditor;
import com.intellij.util.ui.table.ComboBoxTableCellEditor;
import com.intellij.util.ui.table.IconTableCellRenderer;
import com.intellij.util.ui.table.TableModelEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.UUID;

import static com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily;
import static com.intellij.ide.browsers.WebBrowserManager.DefaultBrowser;
import static com.intellij.util.ui.table.TableModelEditor.EditableColumnInfo;

public class BrowserSettingsPanel {
  private static final FileChooserDescriptor APP_FILE_CHOOSER_DESCRIPTOR =
    SystemInfo.isMac ? new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return file.getName().endsWith(".app");
      }
    } : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();

  private static final ColumnInfo[] COLUMNS = {new EditableColumnInfo<ConfigurableWebBrowser, Boolean>() {
    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public Boolean valueOf(ConfigurableWebBrowser item) {
      return item.isActive();
    }

    @Override
    public void setValue(ConfigurableWebBrowser item, Boolean value) {
      item.setActive(value);
    }
  }, new EditableColumnInfo<ConfigurableWebBrowser, String>("Name") {
    @Override
    public String valueOf(ConfigurableWebBrowser item) {
      return item.getName();
    }

    @Override
    public void setValue(ConfigurableWebBrowser item, String value) {
      item.setName(value);
    }
  }, new EditableColumnInfo<ConfigurableWebBrowser, BrowserFamily>("Family") {
    @Override
    public Class getColumnClass() {
      return BrowserFamily.class;
    }

    @Override
    public BrowserFamily valueOf(ConfigurableWebBrowser item) {
      return item.getFamily();
    }

    @Override
    public void setValue(ConfigurableWebBrowser item, BrowserFamily value) {
      item.setFamily(value);
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
    public String valueOf(ConfigurableWebBrowser item) {
      return item.getPath();
    }

    @Override
    public void setValue(ConfigurableWebBrowser item, String value) {
      item.setPath(value);
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(ConfigurableWebBrowser item) {
      return new LocalPathCellEditor().fileChooserDescriptor(APP_FILE_CHOOSER_DESCRIPTOR);
    }
  }};

  private JPanel root;

  private TextFieldWithBrowseButton alternativeBrowserPathField;

  private JCheckBox confirmExtractFiles;
  private JButton clearExtractedFiles;
  private JPanel defaultBrowserPanel;

  @SuppressWarnings("UnusedDeclaration")
  private JComponent browsersTable;

  private ComboBox defaultBrowser;

  private TableModelEditor<ConfigurableWebBrowser> browsersEditor;

  public BrowserSettingsPanel() {
    defaultBrowserPanel.setBorder(IdeBorderFactory.createTitledBorder("Default Browser", false));

    alternativeBrowserPathField.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null,
                                                        APP_FILE_CHOOSER_DESCRIPTOR);

    //noinspection unchecked
    defaultBrowser.setModel(new EnumComboBoxModel<DefaultBrowser>(DefaultBrowser.class));
    if (BrowserUtil.canStartDefaultBrowser()) {
      defaultBrowser.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          alternativeBrowserPathField.setEnabled(getDefaultBrowser() == DefaultBrowser.ALTERNATIVE);
        }
      });

      defaultBrowser.setRenderer(new ListCellRendererWrapper<DefaultBrowser>() {
        @Override
        public void customize(JList list, DefaultBrowser value, int index, boolean selected, boolean hasFocus) {
          String name;
          switch (value) {
            case SYSTEM:
              name = "System";
              break;
            case FIRST:
              name = "First";
              break;
            case ALTERNATIVE:
              name = "Command";
              break;
            default: throw new IllegalStateException();
          }

          setText(name);
        }
      });
    }
    else {
      defaultBrowser.setVisible(false);
    }

    clearExtractedFiles.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        BrowserUtil.clearExtractedFiles();
      }
    });
  }

  private void createUIComponents() {
    TableModelEditor.DialogItemEditor<ConfigurableWebBrowser> itemEditor = new TableModelEditor.DialogItemEditor<ConfigurableWebBrowser>() {
      @NotNull
      @Override
      public Class<ConfigurableWebBrowser> getItemClass() {
        return ConfigurableWebBrowser.class;
      }

      @Override
      public ConfigurableWebBrowser clone(@NotNull ConfigurableWebBrowser item, boolean forInPlaceEditing) {
        return new ConfigurableWebBrowser(forInPlaceEditing ? item.getId() : UUID.randomUUID(),
                                          item.getFamily(), item.getName(), item.getPath(), item.isActive(),
                                          forInPlaceEditing ? item.getSpecificSettings() : cloneSettings(item));
      }

      @Override
      public void edit(@NotNull ConfigurableWebBrowser browser, @NotNull Function<ConfigurableWebBrowser, ConfigurableWebBrowser> mutator) {
        BrowserSpecificSettings settings = cloneSettings(browser);
        if (settings != null && ShowSettingsUtil.getInstance().editConfigurable(browsersTable, settings.createConfigurable())) {
          mutator.fun(browser).setSpecificSettings(settings);
        }
      }

      @Nullable
      private BrowserSpecificSettings cloneSettings(@NotNull ConfigurableWebBrowser browser) {
        BrowserSpecificSettings settings = browser.getSpecificSettings();
        if (settings == null) {
          return null;
        }

        BrowserSpecificSettings newSettings = browser.getFamily().createBrowserSpecificSettings();
        assert newSettings != null;
        TableModelEditor.cloneUsingXmlSerialization(settings, newSettings);
        return newSettings;
      }

      @Override
      public void applyEdited(@NotNull ConfigurableWebBrowser oldItem, @NotNull ConfigurableWebBrowser newItem) {
        oldItem.setSpecificSettings(newItem.getSpecificSettings());
      }
    };
    browsersEditor = new TableModelEditor<ConfigurableWebBrowser>(Collections.<ConfigurableWebBrowser>emptyList(), COLUMNS,
                                                                  itemEditor, "No web browsers configured"
    );
    browsersTable = browsersEditor.createComponent();
  }

  @NotNull
  public JPanel getComponent() {
    return root;
  }

  public boolean isModified() {
    GeneralSettings settings = GeneralSettings.getInstance();
    if (!Comparing.strEqual(settings.getBrowserPath(), alternativeBrowserPathField.getText()) ||
        settings.isConfirmExtractFiles() != confirmExtractFiles.isSelected()) {
      return true;
    }

    WebBrowserManager browserManager = WebBrowserManager.getInstance();
    return browserManager.getDefaultBrowser() != getDefaultBrowser() || browsersEditor.isModified(browserManager.getList());
  }

  public void apply() throws ConfigurationException {
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setUseDefaultBrowser(getDefaultBrowser() == DefaultBrowser.SYSTEM);
    settings.setBrowserPath(alternativeBrowserPathField.getText());
    settings.setConfirmExtractFiles(confirmExtractFiles.isSelected());

    WebBrowserManager browserManager = WebBrowserManager.getInstance();
    browserManager.defaultBrowser = getDefaultBrowser();
    browserManager.setList(browsersEditor.apply());
  }

  private DefaultBrowser getDefaultBrowser() {
    return (DefaultBrowser)defaultBrowser.getSelectedItem();
  }

  public void reset() {
    GeneralSettings settings = GeneralSettings.getInstance();
    alternativeBrowserPathField.setText(settings.getBrowserPath());

    defaultBrowser.setSelectedItem(WebBrowserManager.getInstance().defaultBrowser);
    alternativeBrowserPathField.setEnabled(getDefaultBrowser() == DefaultBrowser.ALTERNATIVE);

    confirmExtractFiles.setSelected(settings.isConfirmExtractFiles());
    browsersEditor.reset(WebBrowserManager.getInstance().getList());
  }
}