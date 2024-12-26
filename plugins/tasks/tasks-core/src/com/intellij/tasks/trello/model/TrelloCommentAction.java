// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.trello.model;

import com.intellij.tasks.Comment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@SuppressWarnings("unused")
public class TrelloCommentAction extends Comment {
  private String id;
  private Date date;
  private TrelloUser memberCreator;
  private Data data;


  @SuppressWarnings("FieldMayBeFinal")
  private static class Data {
    private String text = "";
    private TrelloBoard board;
    private TrelloCard card;
  }

  @Override
  public String toString() {
    return String.format("TrelloCommentAction(id=%s, text=%s)", id, data.text);
  }

  public @NotNull String getId() {
    return id;
  }

  @Override
  public @NotNull Date getDate() {
    return date;
  }

  @Override
  public @NotNull String getText() {
    return data.text;
  }

  @Override
  public @Nullable String getAuthor() {
    // if user deleted it's account in web-interface his comments left
    // marked with [deleted account]
    return memberCreator == null? "[deleted account]": memberCreator.getName();
  }
}
