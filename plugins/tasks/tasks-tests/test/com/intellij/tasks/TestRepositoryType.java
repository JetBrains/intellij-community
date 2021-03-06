// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class TestRepositoryType extends TaskRepositoryType<TestRepository> {
  @NotNull
  @Override
  public String getName() {
    return "Test";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Unknown;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(TestRepository repository, Project project, Consumer<? super TestRepository> changeListener) {
    return new BaseRepositoryEditor<>(project, repository, repository1 -> {

    });
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    TestRepository repository = new TestRepository();
    repository.setRepositoryType(this);
    return repository;
  }

  @Override
  public Class<TestRepository> getRepositoryClass() {
    return TestRepository.class;
  }
}
