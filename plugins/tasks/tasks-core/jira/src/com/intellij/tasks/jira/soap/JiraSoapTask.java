// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.jira.soap;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Comment;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.jira.JiraTask;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Slightly refactored original version of {@link JIRAIssue} adapter for SOAP version of JIRA API.
 *
 * @author Mikhail Golubev
 * @author Dmitry Avdeev
 */
final class JiraSoapTask extends JiraTask {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

  private final String myKey;
  private final @Nls String mySummary;
  private final @Nls String myDescription;
  private final String myIconUrl;
  private final Date myUpdated;
  private final Date myCreated;
  private final TaskState myState;
  private final TaskType myType;

  private final List<Comment> myComments;

  JiraSoapTask(@NotNull Element element, @NotNull TaskRepository repository) {
    super(repository);
    myKey = element.getChildText("key");
    //noinspection HardCodedStringLiteral
    mySummary = element.getChildText("summary");
    //noinspection HardCodedStringLiteral
    myDescription = element.getChildText("description");

    myIconUrl = getChildAttribute(element, "type", "iconUrl");

    myType = getTypeByName(element.getChildText("type"));

    String statusIdText = getChildAttribute(element, "status", "id");
    myState = getStateById(StringUtil.isEmpty(statusIdText) ? 0 : Integer.parseInt(statusIdText));

    myCreated = parseDate(element.getChildText("created"));
    myUpdated = parseDate(element.getChildText("updated"));

    Element comments = element.getChild("comments");
    if (comments != null) {
      myComments = ContainerUtil.map(comments.getChildren("comment"), element1 -> new Comment() {
        @Override
        public String getText() {
          return element1.getText();
        }

        @Override
        public @Nullable String getAuthor() {
          //noinspection HardCodedStringLiteral
          return element1.getAttributeValue("author");
        }

        @Override
        public @Nullable Date getDate() {
          return parseDate(element1.getAttributeValue("created"));
        }
      });
    } else {
      myComments = ContainerUtil.emptyList();
    }
  }

  @Override
  public @NotNull String getId() {
    return myKey;
  }

  @Override
  public @NotNull String getSummary() {
    return mySummary;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public Comment @NotNull [] getComments() {
    return myComments.toArray(Comment.EMPTY_ARRAY);
  }

  @Override
  protected @Nullable String getIconUrl() {
    return myIconUrl;
  }

  @Override
  public @NotNull TaskType getType() {
    return myType;
  }

  @Override
  public TaskState getState() {
    return myState;
  }

  @Override
  public @Nullable Date getUpdated() {
    return myUpdated;
  }

  @Override
  public Date getCreated() {
    return myCreated;
  }

  private static @Nullable Date parseDate(@NotNull String date) {
    try {
      return DATE_FORMAT.parse(date);
    }
    catch (ParseException e) {
      return null;
    }
  }

  private static @Nullable String getChildAttribute(@NotNull Element parent, @NotNull String childName, @NotNull String attributeName) {
    Element child = parent.getChild(childName);
    if (child == null) {
      return null;
    }
    return child.getAttributeValue(attributeName);
  }
}
