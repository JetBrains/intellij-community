// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.trello;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
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

  @Override
  public @NotNull String getName() {
    return "Trello";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Trello;
  }

  @Override
  public @Nullable String getAdvertiser() {
    return TaskBundle.message("html.a.href.0.where.can.i.get.authorization.token.a.html", CLIENT_AUTHORIZATION_URL);
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(TrelloRepository repository, Project project, Consumer<? super TrelloRepository> changeListener) {
    return new TrelloRepositoryEditor(project, repository, changeListener);
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new TrelloRepository(this);
  }

  @Override
  public Class<TrelloRepository> getRepositoryClass() {
    return TrelloRepository.class;
  }
}
