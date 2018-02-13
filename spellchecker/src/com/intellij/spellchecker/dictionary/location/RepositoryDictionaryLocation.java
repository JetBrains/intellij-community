// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary.location;

import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class RepositoryDictionaryLocation implements DictionaryLocation {

  private final Project myProject;

  public RepositoryDictionaryLocation(@NotNull Project project){
    myProject = project;
  }

  @NotNull
  @Override
  public String getName() {
    return SpellCheckerBundle.message("dictionary.location.web");
  }

  @Override
  public void findAndAddNewDictionary(@NotNull Consumer<String> consumer) {
    new DownloadDictionaryDialog(myProject, consumer).showAndGet();
  }
}
