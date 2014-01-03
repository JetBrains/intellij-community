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
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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

  private final Map<WebBrowserSettings, MutableWebBrowserSettings> modifiedBrowsers = new THashMap<WebBrowserSettings, MutableWebBrowserSettings>();

  public BrowserSettingsPanel() {
    defaultBrowserPanel.setBorder(IdeBorderFactory.createTitledBorder("Default Browser", true));

    FileChooserDescriptor descriptor = SystemInfo.isMac ?
                                       new FileChooserDescriptor(false, true, false, false, false, false) {
                                         @Override
                                         public boolean isFileSelectable(VirtualFile file) {
                                           return file.getName().endsWith(".app");
                                         }
                                       } : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    alternativeBrowserPathField.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null, descriptor);

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
    ColumnInfo<WebBrowserSettings, Boolean> activeColumn = new ColumnInfo<WebBrowserSettings, Boolean>("") {
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
    };
    ColumnInfo[] columns = {activeColumn, new ColumnInfo<WebBrowserSettings, String>("Name") {
      @Override
      public String valueOf(WebBrowserSettings info) {
        return getEffective(info).getName();
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

    //browsersPanel.apply();
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

    //browsersPanel.reset();
  }

  public void disposeUIResources() {
    //browsersPanel.dispose();
  }
}