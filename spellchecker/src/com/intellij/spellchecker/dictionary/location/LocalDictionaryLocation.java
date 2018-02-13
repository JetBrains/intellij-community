// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary.location;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;

public class LocalDictionaryLocation implements DictionaryLocation {
  private final Project myProject;

  public LocalDictionaryLocation(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getName() {
    return SpellCheckerBundle.message("dictionary.location.computer");
  }

  @Override
  public void findAndAddNewDictionary(@NotNull Consumer<String> consumer) {
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
                                          .forEach(consumer));
  }
}
