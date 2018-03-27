/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.tasks;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.util.Consumer;
import icons.TasksIcons;
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
    return TasksIcons.Unknown;
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(TestRepository repository, Project project, Consumer<TestRepository> changeListener) {
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
