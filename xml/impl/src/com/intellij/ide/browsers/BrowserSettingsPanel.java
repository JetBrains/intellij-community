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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.LocalPathCellEditor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

import static com.intellij.ide.browsers.WebBrowserSettings.MutableWebBrowserSettings;

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

  private final THashMap<WebBrowserSettings, MutableWebBrowserSettings> modifiedBrowsers = new THashMap<WebBrowserSettings, MutableWebBrowserSettings>();
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

  private WebBrowserSettings getEffective(WebBrowserSettings info) {
    MutableWebBrowserSettings mutable = modifiedBrowsers.isEmpty() ? null : modifiedBrowsers.get(info);
    return mutable == null ? info : mutable;
  }

  private MutableWebBrowserSettings getMutable(WebBrowserSettings info) {
    MutableWebBrowserSettings mutable = modifiedBrowsers.get(info);
    if (mutable == null) {
      mutable = info.createMutable();
      modifiedBrowsers.put(info, mutable);
    }
    return mutable;
  }

  private void createUIComponents() {
    ColumnInfo[] columns = {new ColumnInfo<WebBrowserSettings, Boolean>("") {
      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }

      @Override
      public Boolean valueOf(WebBrowserSettings info) {
        return getEffective(info).isActive();
      }

      @Override
      public boolean isCellEditable(WebBrowserSettings info) {
        return true;
      }

      @Override
      public void setValue(WebBrowserSettings info, Boolean value) {
        getMutable(info).setActive(value);
      }
    }, new ColumnInfo<WebBrowserSettings, String>("Name") {
      @Override
      public String valueOf(WebBrowserSettings info) {
        return getEffective(info).getName();
      }

      @Override
      public boolean isCellEditable(WebBrowserSettings info) {
        return true;
      }

      @Override
      public void setValue(WebBrowserSettings info, String value) {
        getMutable(info).setName(value);
      }
    }, new ColumnInfo<WebBrowserSettings, String>("Path") {
      @Override
      public String valueOf(WebBrowserSettings info) {
        return getEffective(info).getPath();
      }

      @Override
      public boolean isCellEditable(WebBrowserSettings info) {
        return true;
      }

      @Override
      public void setValue(WebBrowserSettings info, String value) {
        getMutable(info).setPath(value);
      }

      @Nullable
      @Override
      public TableCellEditor getEditor(WebBrowserSettings info) {
        return new LocalPathCellEditor(null).fileChooserDescriptor(appFileChooserDescriptor);
      }
    }};
    ListTableModel<WebBrowserSettings> tableModel = new ListTableModel<WebBrowserSettings>(columns, WebBrowserManager.getInstance().getInfos());
    TableView<WebBrowserSettings> table = new TableView<WebBrowserSettings>(tableModel);
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
      for (Map.Entry<WebBrowserSettings, MutableWebBrowserSettings> entry : modifiedBrowsers.entrySet()) {
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
      WebBrowserManager.getInstance().apply(modifiedBrowsers);
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