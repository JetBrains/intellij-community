// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.config;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import com.intellij.tasks.TaskRepositoryType;
import org.jetbrains.annotations.NotNull;

final class TaskSearchableOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    for (TaskRepositoryType<?> type : TaskRepositoryType.getRepositoryTypes()) {
      processor.addOptions(type.getName(), null, null, TaskRepositoriesConfigurable.ID, null, true);
    }
  }
}
