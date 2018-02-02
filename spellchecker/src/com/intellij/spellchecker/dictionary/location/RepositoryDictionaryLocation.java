// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary.location;

import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NotNull;

public class RepositoryDictionaryLocation implements DictionaryLocation {

  private final Project myProject;
  private final TableView<String> myTableView;

  public RepositoryDictionaryLocation(@NotNull Project project, TableView<String> tableView){
    myProject = project;
    myTableView = tableView;
  }

  @NotNull
  @Override
  public String getName() {
    return SpellCheckerBundle.message("dictionary.location.web");
  }

  @Override
  public void findAndAddNewDictionary() {
    new DownloadDictionaryDialog(myProject, myTableView).showAndGet();
  }
}
