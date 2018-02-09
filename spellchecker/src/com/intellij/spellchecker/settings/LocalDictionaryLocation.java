// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.settings;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.table.TableView;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;

class LocalDictionaryLocation implements DictionaryLocation {
  private final Project myProject;
  private final TableView<String> myTableView;

  LocalDictionaryLocation(@NotNull Project project, @NotNull TableView<String> tableView) {
    myProject = project;
    myTableView = tableView;
  }

  @NotNull
  @Override
  public String getName() {
    return SpellCheckerBundle.message("dictionary.location.computer");
  }

  @Override
  public void findAndAddNewDictionary() {
    final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return extensionEquals(file.getPath(), "dic");
      }
    };

    FileChooser.chooseFiles(fileChooserDescriptor, myProject, null, myProject.getBaseDir(),
                            files -> files.stream()
                                          .map(VirtualFile::getPath)
                                          .map(PathUtil::toSystemDependentName)
                                          .filter(path -> !myTableView.getItems().contains(path))
                                          .forEach(path -> myTableView.getListTableModel().addRow(path)));
  }
}
