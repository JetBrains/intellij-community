// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.trello;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.trello.model.TrelloCard;
import com.intellij.tasks.trello.model.TrelloCommentAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class TrelloTask extends Task {
  private static final TrelloIconBuilder ourIconBuilder = new TrelloIconBuilder(16);

  private final TrelloCard myCard;
  private final TaskRepository myRepository;


  public TrelloTask(TrelloCard card, TaskRepository repository) {
    myCard = card;
    myRepository = repository;
  }

  @Override
  public @NotNull String getId() {
    return myCard.getId();
  }

  @Override
  public @NotNull String getSummary() {
    return myCard.getName();
  }

  @Override
  public @Nullable String getDescription() {
    return myCard.getDescription();
  }

  @Override
  public Comment @NotNull [] getComments() {
    List<TrelloCommentAction> comments = myCard.getComments();
    return comments.toArray(Comment.EMPTY_ARRAY);
  }

  @Override
  public @NotNull Icon getIcon() {
    return ourIconBuilder.buildIcon(myCard.getColors());
  }

  @Override
  public @NotNull TaskType getType() {
    return TaskType.OTHER;
  }

  @Override
  public @Nullable Date getUpdated() {
    return myCard.getDateLastActivity();
  }

  @Override
  public @Nullable Date getCreated() {
    return null;
  }

  @Override
  public boolean isClosed() {
    // IDEA-111470, IDEA-111475
    return myCard.isClosed() || !myCard.isVisible();
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Override
  public @Nullable String getIssueUrl() {
    return myCard.getUrl();
  }

  @Override
  public String getPresentableName() {
    return myCard.getName();
  }

  @Override
  public @Nullable TaskRepository getRepository() {
    return myRepository;
  }

  @Override
  public @NotNull String getNumber() {
    return myCard.getIdShort();
  }
}
