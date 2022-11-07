// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.spellchecker.state.ProjectDictionaryState;
import org.jetbrains.annotations.NotNull;

import static com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel.APP;
import static com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel.PROJECT;

final class SettingsTransferActivity implements StartupActivity.DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    SpellCheckerSettings settings = SpellCheckerSettings.getInstance(project);
    if (settings.isSettingsTransferred()) {
      return;
    }

    if (settings.isUseSingleDictionaryToSave() &&
        PROJECT.getName().equals(settings.getDictionaryToSave()) &&
        project.getService(ProjectDictionaryState.class).getProjectDictionary().getWords().isEmpty()) {
      settings.setDictionaryToSave(APP.getName());
    }
    settings.setSettingsTransferred(true);
  }
}
