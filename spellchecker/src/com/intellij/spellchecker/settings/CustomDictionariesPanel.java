// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.settings;


import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;

public class CustomDictionariesPanel extends JPanel {
  private SpellCheckerSettings mySettings;
  private CustomDictionariesTableView myCustomDictionariesTableView;
  @NotNull private final Project myProject;
  private final List<String> removedDictionaries = new ArrayList<>();

  public CustomDictionariesPanel(@NotNull SpellCheckerSettings settings, @NotNull Project project) {
    mySettings = settings;
    myCustomDictionariesTableView = new CustomDictionariesTableView(new ArrayList<>(settings.getCustomDictionariesPaths()),
                                                                    new ArrayList<>(settings.getDisabledDictionariesPaths()));
    myProject = project;
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myCustomDictionariesTableView)

      .setAddActionName(SpellCheckerBundle.message("add.custom.dictionaries"))
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          myCustomDictionariesTableView.stopEditing();
          doChooseFiles(project, files -> files.stream()
            .map(VirtualFile::getPath)
            .map(PathUtil::toSystemDependentName)
            .filter(path -> !myCustomDictionariesTableView.getItems().contains(path))
            .forEach(path -> myCustomDictionariesTableView.getListTableModel().addRow(path)));
        }
      })

      .setRemoveActionName(SpellCheckerBundle.message("remove.custom.dictionaries"))
      .setRemoveAction(button -> {
        removedDictionaries.addAll(myCustomDictionariesTableView.getSelectedObjects());
        TableUtil.removeSelectedItems(myCustomDictionariesTableView);
      })

      .setEditActionName(SpellCheckerBundle.message("edit.custom.dictionary"))
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          final String filePath = myCustomDictionariesTableView.getSelectedObject();
          final VirtualFile file = StringUtil.isEmpty(filePath) ? null : LocalFileSystem
            .getInstance().refreshAndFindFileByPath(filePath);
          if (file == null) {
            final String title = SpellCheckerBundle.message("custom.dictionary.not.found.title");
            final String message = SpellCheckerBundle.message("custom.dictionary.not.found", filePath);
            Messages.showMessageDialog(myProject, message, title, Messages.getErrorIcon());
            return;
          }

          final FileEditorManager fileManager = FileEditorManager.getInstance(myProject);
          if (fileManager != null) {
            fileManager.openFile(file, true);
          }
        }
      })

      .disableUpDownActions();
    myCustomDictionariesTableView.getEmptyText().setText((SpellCheckerBundle.message("no.custom.dictionaries")));
    this.setLayout(new BorderLayout());
    this.add(decorator.createPanel(), BorderLayout.CENTER);
  }

  private void doChooseFiles(@NotNull Project project, @NotNull Consumer<List<VirtualFile>> consumer) {
    final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return extensionEquals(file.getPath(), "dic");
      }
    };

    FileChooser.chooseFiles(fileChooserDescriptor, project, this.getParent(), project.getBaseDir(), consumer);
  }

  public List<String> getRemovedDictionaries() {
    return removedDictionaries;
  }

  public boolean isModified() {
    final List<String> oldPaths = mySettings.getCustomDictionariesPaths();
    final List<String> newPaths = myCustomDictionariesTableView.getItems();
    if (oldPaths.size() != newPaths.size()) {
      return true;
    }

    final Set<String> oldDisabled = mySettings.getDisabledDictionariesPaths();
    final List<String> newDisabled = myCustomDictionariesTableView.getDisabled();
    if (oldDisabled.size() != newDisabled.size()) {
      return true;
    }
    if (!newPaths.containsAll(oldPaths) || !oldPaths.containsAll(newPaths)) {
      return true;
    }
    if (!newDisabled.containsAll(oldDisabled) || !oldDisabled.containsAll(newDisabled)) {
      return true;
    }
    return false;
  }

  public void reset() {
    myCustomDictionariesTableView.getListTableModel().setItems(new ArrayList<>(mySettings.getCustomDictionariesPaths()));
    myCustomDictionariesTableView.setDisabled(new ArrayList<>(mySettings.getDisabledDictionariesPaths()));
    removedDictionaries.clear();
  }

  public void apply() {
    mySettings.setCustomDictionariesPaths(new ArrayList<>(myCustomDictionariesTableView.getItems()));
    mySettings.setDisabledDictionariesPaths(new HashSet<>(myCustomDictionariesTableView.getDisabled()));
  }

  public List<String> getValues() {
    return myCustomDictionariesTableView.getItems();
  }

  private static class CustomDictionariesTableView extends TableView<String> {

    @NotNull private List<String> myDisabled;

    private CustomDictionariesTableView(@NotNull List<String> dictionaries, @NotNull List<String> disabled) {
      myDisabled = disabled;
      setModelAndUpdateColumns(new ListTableModel<>(createDictionaryColumnInfos(), dictionaries, 1));
      setAutoResizeMode(AUTO_RESIZE_LAST_COLUMN);
      getColumnModel().getColumn(0).setResizable(false);
      setShowGrid(false);
      setShowVerticalLines(false);
      setGridColor(getForeground());
      setTableHeader(null);
      TableUtil.setupCheckboxColumn(getColumnModel().getColumn(0));
    }

    @NotNull
    private List<String> getDisabled() {
      return myDisabled;
    }

    public void setDisabled(@NotNull List<String> disabled) {
      myDisabled = disabled;
    }

    private ColumnInfo[] createDictionaryColumnInfos() {
      return new ColumnInfo[]{
        new ColumnInfo<String, Boolean>(" ") {
          @Override
          public Class<?> getColumnClass() {
            return Boolean.class;
          }

          @Override
          public void setValue(String s, Boolean value) {
            if (value) {
              myDisabled.remove(s);
            }
            else {
              myDisabled.add(s);
            }
          }

          @Override
          public boolean isCellEditable(String s) {
            return true;
          }

          @Override
          public Boolean valueOf(final String o) {
            return !myDisabled.contains(o);
          }
        },
        new ColumnInfo<String, String>(SpellCheckerBundle.message("custom.dictionary.title")) {
          @Override
          public String valueOf(final String info) {
            return info;
          }
        }};
    }
  }
}