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
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.LocalPathCellEditor;
import com.intellij.util.ui.table.ComboBoxTableCellEditor;
import com.intellij.util.ui.table.IconTableCellRenderer;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Map;

import static com.intellij.ide.browsers.BrowsersConfiguration.BrowserFamily;

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

  private final THashMap<ConfigurableWebBrowser, ConfigurableWebBrowser> modifiedBrowsers = new THashMap<ConfigurableWebBrowser, ConfigurableWebBrowser>();
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

  private ConfigurableWebBrowser getEffective(ConfigurableWebBrowser info) {
    ConfigurableWebBrowser mutable = modifiedBrowsers.isEmpty() ? null : modifiedBrowsers.get(info);
    return mutable == null ? info : mutable;
  }

  private ConfigurableWebBrowser getMutable(ConfigurableWebBrowser info) {
    ConfigurableWebBrowser mutable = modifiedBrowsers.get(info);
    if (mutable == null) {
      mutable = new ConfigurableWebBrowser(info.getId(), info.getFamily(), info.getName(), info.getPath(), info.isActive(), info.getSpecificSettings());
      modifiedBrowsers.put(info, mutable);
    }
    return mutable;
  }

  private void createUIComponents() {
    ColumnInfo[] columns = {new ColumnInfo<ConfigurableWebBrowser, Boolean>("") {
      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }

      @Override
      public Boolean valueOf(ConfigurableWebBrowser info) {
        return getEffective(info).isActive();
      }

      @Override
      public boolean isCellEditable(ConfigurableWebBrowser info) {
        return true;
      }

      @Override
      public void setValue(ConfigurableWebBrowser info, Boolean value) {
        if (value != info.isActive()) {
          getMutable(info).setActive(value);
        }
      }
    }, new ColumnInfo<ConfigurableWebBrowser, String>("Name") {
      @Override
      public String valueOf(ConfigurableWebBrowser info) {
        return getEffective(info).getName();
      }

      @Override
      public boolean isCellEditable(ConfigurableWebBrowser info) {
        return true;
      }

      @Override
      public void setValue(ConfigurableWebBrowser info, String value) {
        if (!value.equals(info.getName())) {
          getMutable(info).setName(value);
        }
      }
    }, new ColumnInfo<ConfigurableWebBrowser, BrowserFamily>("Family") {
      @Override
      public Class getColumnClass() {
        return BrowserFamily.class;
      }

      @Override
      public BrowserFamily valueOf(ConfigurableWebBrowser info) {
        return getEffective(info).getFamily();
      }

      @Override
      public boolean isCellEditable(ConfigurableWebBrowser info) {
        return true;
      }

      @Override
      public void setValue(ConfigurableWebBrowser info, BrowserFamily value) {
        if (value != info.getFamily()) {
          getMutable(info).setFamily(value);
        }
      }

      @Nullable
      @Override
      public TableCellRenderer getRenderer(ConfigurableWebBrowser info) {
        return IconTableCellRenderer.ICONABLE;
      }

      @Nullable
      @Override
      public TableCellEditor getEditor(ConfigurableWebBrowser o) {
        return ComboBoxTableCellEditor.INSTANCE;
      }
    }, new ColumnInfo<ConfigurableWebBrowser, String>("Path") {
      @Override
      public String valueOf(ConfigurableWebBrowser info) {
        return getEffective(info).getPath();
      }

      @Override
      public boolean isCellEditable(ConfigurableWebBrowser info) {
        return true;
      }

      @Override
      public void setValue(ConfigurableWebBrowser info, String value) {
        if (!value.equals(info.getPath())) {
          getMutable(info).setPath(StringUtil.nullize(value, true));
        }
      }

      @Nullable
      @Override
      public TableCellEditor getEditor(ConfigurableWebBrowser info) {
        return new LocalPathCellEditor(null).fileChooserDescriptor(appFileChooserDescriptor);
      }
    }};
    ListTableModel<ConfigurableWebBrowser> tableModel = new ListTableModel<ConfigurableWebBrowser>(columns, new ArrayList<ConfigurableWebBrowser>(WebBrowserManager.getInstance().getList()));
    TableView<ConfigurableWebBrowser> table = new TableView<ConfigurableWebBrowser>(tableModel);
    table.setStriped(true);
    new TableSpeedSearch(table);
    TableUtil.setupCheckboxColumn(table.getColumnModel().getColumn(0));

    browsersTable = ToolbarDecorator.createDecorator(table).createPanel();
  }

  @NotNull
  public JPanel getComponent() {
    return root;
  }

  public boolean isModified() {
    GeneralSettings settings = GeneralSettings.getInstance();
    boolean isModified = !Comparing.strEqual(settings.getBrowserPath(), alternativeBrowserPathField.getText());
    isModified |= settings.isUseDefaultBrowser() != useSystemDefaultBrowser.isSelected();
    isModified |= settings.isConfirmExtractFiles() != confirmExtractFiles.isSelected();

    if (isModified) {
      return true;
    }

    if (!modifiedBrowsers.isEmpty()) {
      for (Map.Entry<ConfigurableWebBrowser, ConfigurableWebBrowser> entry : modifiedBrowsers.entrySet()) {
        if (entry.getValue().isChanged(entry.getKey())) {
          return true;
        }
      }
    }

    return false;
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

    if (!modifiedBrowsers.isEmpty()) {
      modifiedBrowsers.forEachEntry(new TObjectObjectProcedure<ConfigurableWebBrowser, ConfigurableWebBrowser>() {
        @Override
        public boolean execute(ConfigurableWebBrowser info, ConfigurableWebBrowser newInfo) {
          info.setName(newInfo.getName());
          info.setFamily(newInfo.getFamily());
          info.setPath(newInfo.getPath());
          info.setActive(newInfo.isActive());
          info.setSpecificSettings(newInfo.getSpecificSettings());
          return true;
        }
      });
    }
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

    modifiedBrowsers.clear();
  }

  public void disposeUIResources() {
  }
}