/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.*;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Dmitry Avdeev
*/
@Tag("task")
@SuppressWarnings({"UnusedDeclaration"})
public class LocalTaskImpl extends LocalTask {

  @NonNls static final String DEFAULT_TASK_ID = "Default";

  private final static Icon BUG_ICON = IconLoader.getIcon("/icons/bug.png", LocalTask.class);
  private final static Icon EXCEPTION_ICON = IconLoader.getIcon("/icons/exception.png", LocalTask.class);
  private final static Icon FEATURE_ICON = IconLoader.getIcon("/icons/feature.png", LocalTask.class);
  private final static Icon OTHER_ICON = IconLoader.getIcon("/icons/other.png", LocalTask.class);
  private final static Icon UNKNOWN_ICON = IconLoader.getIcon("/icons/unknown.png", LocalTask.class);

  private String myId = "";
  private String mySummary = "";
  private boolean myActive;
  private boolean myClosed;
  private Date myCreated;
  protected Date myUpdated;
  private List<ChangeListInfo> myChangeLists = new ArrayList<ChangeListInfo>();
  private boolean myIssue;
  private String myIssueUrl;
  private TaskType myType = TaskType.OTHER;
  private String myDescription;
  private Comment[] myComments = new Comment[0];
  private String myAssociatedChangelistId;
  private String myPresentableName;
  private String myCustomIcon;

  /** for serialization */
  public LocalTaskImpl() {    
  }

  public LocalTaskImpl(@NotNull String id, @NotNull String summary) {
    myId = id;
    mySummary = summary;
  }

  public LocalTaskImpl(Task origin) {

    myId = origin.getId();
    myIssue = origin.isIssue();

    copy(origin);

    if (origin instanceof LocalTaskImpl) {
      myChangeLists = ((LocalTaskImpl)origin).getChangeLists();
      myActive = ((LocalTaskImpl)origin).isActive();
      myAssociatedChangelistId = ((LocalTaskImpl)origin).getAssociatedChangelistId();
    }
  }

  @Attribute("id")
  @NotNull
  public String getId() {
    return myId;
  }

  @Attribute("summary")
  @NotNull
  public String getSummary() {
    return mySummary;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  public Comment[] getComments() {
    return myComments;
  }

  @Tag("updated")
  public Date getUpdated() {
    return myUpdated == null ? getCreated() : myUpdated;
  }

  @Tag("created")
  public Date getCreated() {
    if (myCreated == null) {
      myCreated = new Date();
    }
    return myCreated;
  }

  @Attribute("active")
  public boolean isActive() {
    return myActive;
  }

  @Override
  public String getAssociatedChangelistId() {
    return myAssociatedChangelistId;
  }

  @Override
  public void updateFromIssue(Task issue) {
    copy(issue);
    myIssue = true;
  }

  private void copy(Task issue) {
    myCreated = issue.getCreated();
    if (Comparing.compare(myUpdated, issue.getUpdated()) < 0) {
      myUpdated = issue.getUpdated();
    }
    myPresentableName = issue.getPresentableName();    
    mySummary = issue.getSummary();
    myClosed = issue.isClosed();
    myIssueUrl = issue.getIssueUrl();
    myType = issue.getType();
    myDescription = issue.getDescription();
    myComments = issue.getComments();
    myCustomIcon = issue.getCustomIcon();
  }

  public void setId(String id) {
    myId = id;
  }

  public void setSummary(String summary) {
    mySummary = summary;
  }

  public void setActive(boolean active) {
    myActive = active;
  }

  @Override
  public boolean isIssue() {
    return myIssue;
  }

  @Override
  public String getIssueUrl() {
    return myIssueUrl;
  }

  public void setIssue(boolean issue) {
    myIssue = issue;
  }

  public void setCreated(Date created) {
    myCreated = created;
  }

  public void setUpdated(Date updated) {
    myUpdated = updated;
  }

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag="changelist")
  public List<ChangeListInfo> getChangeLists() {
    return myChangeLists;
  }

  public void setChangeLists(List<ChangeListInfo> changeLists) {
    myChangeLists = changeLists;
  }

  @Override
  public void setAssociatedChangelistId(String associatedChangelistId) {
    myAssociatedChangelistId = associatedChangelistId;
  }

  public boolean isClosed() {
    return myClosed;
  }

  public void setClosed(boolean closed) {
    myClosed = closed;
  }

  @Override
  public Icon getIcon() {
    final String customIcon = getCustomIcon();
    if (customIcon != null) {
      return IconLoader.getIcon(customIcon, LocalTask.class);
    }
    switch (myType) {
      case BUG:
        return BUG_ICON;
      case EXCEPTION:
        return EXCEPTION_ICON;
      case FEATURE:
        return FEATURE_ICON;
      default:
      case OTHER:
        return isIssue() ? OTHER_ICON : UNKNOWN_ICON;
    }
  }

  @NotNull
  @Override
  public TaskType getType() {
    return myType;
  }

  public void setType(TaskType type) {
    myType = type == null ? TaskType.OTHER : type;
  }

  @Override
  public boolean isDefault() {
    return myId.equals(DEFAULT_TASK_ID);
  }

  public boolean equalsTo(LocalTask task) {
    return task != null && task.getId().equals(myId);
  }

  @Override
  public String getPresentableName() {
    return myPresentableName != null ? myPresentableName : toString();
  }

  public String getCustomIcon() {
    return myCustomIcon;
  }
}
