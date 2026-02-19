// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.trello.model;

import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.intellij.tasks.trello.model.TrelloLabel.LabelColor;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@SuppressWarnings("UnusedDeclaration")
public class TrelloCard extends TrelloModel {

  public static final String REQUIRED_FIELDS = "closed,desc,idMembers,idBoard,idList,idShort,labels,name,url,dateLastActivity";

  private String idBoard, idList, idShort;
  private List<String> idMembers;
  private @NotNull String name = "";
  @SerializedName("desc")
  private String description;
  private String url;
  private boolean closed;
  private Date dateLastActivity;
  private List<TrelloLabel> labels;
  @SerializedName("actions")
  private final List<TrelloCommentAction> comments = ContainerUtil.emptyList();
  /**
   * This field is not part of card representation downloaded from server
   * and set explicitly in {@link com.intellij.tasks.trello.TrelloRepository}
   */
  private boolean isVisible = true;

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public TrelloCard() {
  }

  @Override
  public String toString() {
    return String.format("TrelloCard(id='%s', name='%s')", getId(), name);
  }

  public @NotNull String getIdBoard() {
    return idBoard;
  }

  public @NotNull String getIdList() {
    return idList;
  }

  public @NotNull String getIdShort() {
    return idShort;
  }

  public @NotNull List<String> getIdMembers() {
    return idMembers;
  }

  @Attribute("name")
  @Override
  public @NotNull @NlsSafe String getName() {
    return name;
  }

  @Override
  public void setName(@NotNull String name) {
    this.name = name;
  }

  public @NotNull @NlsSafe String getDescription() {
    return description;
  }

  public @NotNull String getUrl() {
    return url;
  }

  public boolean isClosed() {
    return closed;
  }

  public @NotNull List<TrelloLabel> getLabels() {
    return labels;
  }

  public @NotNull List<TrelloCommentAction> getComments() {
    return comments;
  }

  /**
   * @return colors of labels with special {@link LabelColor#NO_COLOR} value excluded
   */
  public @NotNull Set<LabelColor> getColors() {
    if (labels == null || labels.isEmpty()) {
      return EnumSet.noneOf(LabelColor.class);
    }
    final List<LabelColor> labelColors = ContainerUtil.mapNotNull(labels, label -> {
      final LabelColor color = label.getColor();
      return color == LabelColor.NO_COLOR ? null : color;
    });
    return labelColors.isEmpty() ? EnumSet.noneOf(LabelColor.class) : EnumSet.copyOf(labelColors);
  }

  public boolean isVisible() {
    return isVisible;
  }

  public void setVisible(boolean visible) {
    isVisible = visible;
  }

  public @Nullable Date getDateLastActivity() {
    return dateLastActivity;
  }
}
