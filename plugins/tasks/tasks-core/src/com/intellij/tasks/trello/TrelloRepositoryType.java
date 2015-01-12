/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.tasks.trello;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class TrelloRepositoryType extends BaseRepositoryType<TrelloRepository> {
  public static final String DEVELOPER_KEY = "d6ec3709f7141007e150de64d4701181";
  public static final String CLIENT_AUTHORIZATION_URL =
    "https://trello.com/1/authorize?key=" + DEVELOPER_KEY +"&name=JetBrains&expiration=never&response_type=token&scope=read,write";

  @NotNull
  @Override
  public String getName() {
    return "Trello";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksIcons.Trello;
  }

  @Nullable
  @Override
  public String getAdvertiser() {
    return "<html><a href=" + CLIENT_AUTHORIZATION_URL + ">Where can I get authorization token?</a></html>";
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(TrelloRepository repository, Project project, Consumer<TrelloRepository> changeListener) {
    return new TrelloRepositoryEditor(project, repository, changeListener);
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new TrelloRepository(this);
  }

  @Override
  public Class<TrelloRepository> getRepositoryClass() {
    return TrelloRepository.class;
  }
}
