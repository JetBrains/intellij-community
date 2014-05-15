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

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.LocalPathCellEditor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.IconTableCellRenderer;
import com.intellij.util.ui.table.TableModelEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.UUID;

import static com.intellij.ide.browsers.WebBrowserManager.DefaultBrowser;
import static com.intellij.util.ui.table.TableModelEditor.EditableColumnInfo;

final class BrowserSettingsPanel {
  private static final FileChooserDescriptor APP_FILE_CHOOSER_DESCRIPTOR = FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor();

  private static final EditableColumnInfo<ConfigurableWebBrowser, String> PATH_COLUMN_INFO =
    new EditableColumnInfo<ConfigurableWebBrowser, String>("Path") {
      @Override
      public String valueOf(ConfigurableWebBrowser item) {
        return PathUtil.toSystemDependentName(item.getPath());
      }

      @Override
      public void setValue(ConfigurableWebBrowser item, String value) {
        item.setPath(value);
      }

      @Nullable
      @Override
      public TableCellEditor getEditor(ConfigurableWebBrowser item) {
        return new LocalPathCellEditor().fileChooserDescriptor(APP_FILE_CHOOSER_DESCRIPTOR).normalizePath(true);
      }
    };

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
  }, new ColumnInfo<ConfigurableWebBrowser, BrowserFamily>("Family") {
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
      item.setSpecificSettings(value.createBrowserSpecificSettings());
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(ConfigurableWebBrowser item) {
      return IconTableCellRenderer.ICONABLE;
    }

    @Override
    public boolean isCellEditable(ConfigurableWebBrowser item) {
      return !WebBrowserManager.getInstance().isPredefinedBrowser(item);
    }
  }, PATH_COLUMN_INFO};

  private JPanel root;

  private TextFieldWithBrowseButton alternativeBrowserPathField;

  private JCheckBox confirmExtractFiles;
  private JButton clearExtractedFiles;
  private JPanel defaultBrowserPanel;

  @SuppressWarnings("UnusedDeclaration")
  private JComponent browsersTable;

  private ComboBox defaultBrowserComboBox;

  private TableModelEditor<ConfigurableWebBrowser> browsersEditor;

  private String customPathValue;

  public BrowserSettingsPanel() {
    alternativeBrowserPathField.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null, APP_FILE_CHOOSER_DESCRIPTOR);
    defaultBrowserPanel.setBorder(TitledSeparator.EMPTY_BORDER);

    //noinspection unchecked
    defaultBrowserComboBox.setModel(new EnumComboBoxModel<DefaultBrowser>(DefaultBrowser.class));
    if (BrowserLauncherAppless.canStartDefaultBrowser()) {
      defaultBrowserComboBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          boolean customPathEnabled = e.getItem() == DefaultBrowser.ALTERNATIVE;
          if (e.getStateChange() == ItemEvent.DESELECTED) {
            if (customPathEnabled) {
              customPathValue = alternativeBrowserPathField.getText();
            }
          }
          else if (e.getStateChange() == ItemEvent.SELECTED) {
            alternativeBrowserPathField.setEnabled(customPathEnabled);
            updateCustomPathTextFieldValue((DefaultBrowser)e.getItem());
          }
        }
      });

      defaultBrowserComboBox.setRenderer(new ListCellRendererWrapper<DefaultBrowser>() {
        @Override
        public void customize(JList list, DefaultBrowser value, int index, boolean selected, boolean hasFocus) {
          String name;
          switch (value) {
            case SYSTEM:
              name = "System default";
              break;
            case FIRST:
              name = "First listed";
              break;
            case ALTERNATIVE:
              name = "Custom path";
              break;
            default:
              throw new IllegalStateException();
          }

          setText(name);
        }
      });

      if (UIUtil.isUnderAquaLookAndFeel()) {
        defaultBrowserComboBox.setBorder(new EmptyBorder(3, 0, 0, 0));
      }
    }
    else {
      defaultBrowserComboBox.setVisible(false);
    }

    clearExtractedFiles.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        BrowserLauncherAppless.clearExtractedFiles();
      }
    });
  }

  private void updateCustomPathTextFieldValue(DefaultBrowser browser) {
    if (browser == DefaultBrowser.ALTERNATIVE) {
      alternativeBrowserPathField.setText(customPathValue);
    }
    else if (browser == DefaultBrowser.FIRST) {
      setCustomPathToFirstListed();
    }
    else {
      alternativeBrowserPathField.setText("");
    }
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
      public void edit(@NotNull ConfigurableWebBrowser browser, @NotNull Function<ConfigurableWebBrowser, ConfigurableWebBrowser> mutator, boolean isAdd) {
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

      @Override
      public boolean isEditable(@NotNull ConfigurableWebBrowser browser) {
        return browser.getSpecificSettings() != null;
      }

      @Override
      public boolean isRemovable(@NotNull ConfigurableWebBrowser item) {
        return !WebBrowserManager.getInstance().isPredefinedBrowser(item);
      }
    };
    browsersEditor = new TableModelEditor<ConfigurableWebBrowser>(COLUMNS, itemEditor, "No web browsers configured")
      .modelListener(new TableModelEditor.DataChangedListener<ConfigurableWebBrowser>() {
        @Override
        public void tableChanged(TableModelEvent event) {
          update(event.getFirstRow());
        }

        @Override
        public void dataChanged(@NotNull ColumnInfo<ConfigurableWebBrowser, ?> columnInfo, int rowIndex) {
          if (columnInfo == PATH_COLUMN_INFO) {
            update(rowIndex);
          }
        }

        private void update(int rowIndex) {
          if (rowIndex == 0 && getDefaultBrowser() == DefaultBrowser.FIRST) {
            setCustomPathToFirstListed();
          }
        }
      });
    browsersTable = browsersEditor.createComponent();
  }

  private void setCustomPathToFirstListed() {
    ListTableModel<ConfigurableWebBrowser> model = browsersEditor.getModel();
    alternativeBrowserPathField.setText(model.getRowCount() == 0 ? "" : model.getRowValue(0).getPath());
  }

  @NotNull
  public JPanel getComponent() {
    return root;
  }

  public boolean isModified() {
    WebBrowserManager browserManager = WebBrowserManager.getInstance();
    GeneralSettings generalSettings = GeneralSettings.getInstance();

    DefaultBrowser defaultBrowser = getDefaultBrowser();
    if (browserManager.getDefaultBrowserMode() != defaultBrowser || generalSettings.isConfirmExtractFiles() != confirmExtractFiles.isSelected()) {
      return true;
    }

    if (defaultBrowser == DefaultBrowser.ALTERNATIVE &&
        !Comparing.strEqual(generalSettings.getBrowserPath(), alternativeBrowserPathField.getText())) {
      return true;
    }

    return browsersEditor.isModified(browserManager.getList());
  }

  public void apply() {
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setUseDefaultBrowser(getDefaultBrowser() == DefaultBrowser.SYSTEM);

    if (alternativeBrowserPathField.isEnabled()) {
      settings.setBrowserPath(alternativeBrowserPathField.getText());
    }

    settings.setConfirmExtractFiles(confirmExtractFiles.isSelected());

    WebBrowserManager browserManager = WebBrowserManager.getInstance();
    browserManager.defaultBrowser = getDefaultBrowser();
    browserManager.setList(browsersEditor.apply());
  }

  private DefaultBrowser getDefaultBrowser() {
    return (DefaultBrowser)defaultBrowserComboBox.getSelectedItem();
  }

  public void reset() {
    GeneralSettings settings = GeneralSettings.getInstance();

    DefaultBrowser defaultBrowser = WebBrowserManager.getInstance().getDefaultBrowserMode();
    defaultBrowserComboBox.setSelectedItem(defaultBrowser);

    confirmExtractFiles.setSelected(settings.isConfirmExtractFiles());
    browsersEditor.reset(WebBrowserManager.getInstance().getList());

    customPathValue = settings.getBrowserPath();
    alternativeBrowserPathField.setEnabled(defaultBrowser == DefaultBrowser.ALTERNATIVE);
    updateCustomPathTextFieldValue(defaultBrowser);
  }

  public void selectBrowser(@NotNull WebBrowser browser) {
    if (browser instanceof ConfigurableWebBrowser) {
      browsersEditor.selectItem((ConfigurableWebBrowser)browser);
    }
  }
}