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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dmitry Avdeev
 */
public class TaskUtil {
  private static SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
  // Almost ISO-8601 strict except date parts may be separated by '/'
  // and date only also allowed just in case
  private static Pattern ISO8601_DATE_PATTERN = Pattern.compile(
    "(\\d{4}[/-]\\d{2}[/-]\\d{2})" +                  // date
    "(?:[ T](\\d{2}:\\d{2}:\\d{2})(.\\d{3,})?" +      // optional time and milliseconds
    "([+-]\\d{2}:\\d{2}|[+-]\\d{4}|[+-]\\d{2}|Z)?)?");// optional timezone info

  public static String formatTask(@NotNull Task task, String format) {
    return format.replace("{id}", task.getId()).replace("{number}", task.getNumber())
      .replace("{project}", task.getProject() == null ? "" : task.getProject()).replace("{summary}", task.getSummary());
  }

  @Nullable
  public static String getChangeListComment(Task task) {
    final TaskRepository repository = task.getRepository();
    if (repository == null || !repository.isShouldFormatCommitMessage()) {
      return null;
    }
    return formatTask(task, repository.getCommitMessageFormat());
  }

  public static String getTrimmedSummary(Task task) {
    String text;
    if (task.isIssue()) {
      text = task.getId() + ": " + task.getSummary();
    }
    else {
      text = task.getSummary();
    }
    return StringUtil.first(text, 60, true);
  }

  @Nullable
  public static Date parseDate(@NotNull String s) {
    // SimpleDateFormat prior JDK7 doesn't support 'X' specifier for ISO 8601 timezone format.
    // Because some bug trackers and task servers e.g. send dates ending with 'Z' (that stands for UTC),
    // dates should be preprocessed before parsing.
    Matcher m = ISO8601_DATE_PATTERN.matcher(s);
    if (!m.matches()) {
      return null;
    }
    String datePart = m.group(1).replace('/', '-');
    String timePart = m.group(2);
    if (timePart == null) {
      timePart = "00:00:00";
    }
    String milliseconds = m.group(3);
    milliseconds = milliseconds == null ? "000" : milliseconds.substring(1, 4);
    String timezone = m.group(4);
    if (timezone == null || timezone.equals("Z")) {
      timezone = "+0000";
    }
    else if (timezone.length() == 3) {
      // [+-]HH
      timezone += "00";
    }
    else if (timezone.length() == 6) {
      // [+-]HH:MM
      timezone = timezone.substring(0, 3) + timezone.substring(4, 6);
    }
    String canonicalForm = String.format("%sT%s.%s%s", datePart, timePart, milliseconds, timezone);
    try {
      return ISO8601_DATE_FORMAT.parse(canonicalForm);
    }
    catch (ParseException e) {
      return null;
    }
  }

  /**
   * {@link Task#equals(Object)} implementation compares tasks by they unique IDs only.
   * This method should be used when full comparison is necessary.
   */
  public static boolean tasksEqual(@NotNull Task t1, @NotNull Task t2) {
    if (!t1.getId().equals(t2.getId())) return false;
    if (!t1.getSummary().equals(t2.getSummary())) return false;
    if (t1.isClosed() != t2.isClosed()) return false;
    if (t1.isIssue() != t2.isIssue()) return false;
    if (!Comparing.equal(t1.getState(), t2.getState())) return false;
    if (!Comparing.equal(t1.getType(), t2.getType())) return false;
    if (!Comparing.equal(t1.getDescription(), t2.getDescription())) return false;
    if (!Comparing.equal(t1.getCreated(), t2.getCreated())) return false;
    if (!Comparing.equal(t1.getUpdated(), t2.getUpdated())) return false;
    if (!Comparing.equal(t1.getIssueUrl(), t2.getIssueUrl())) return false;
    if (!Comparing.equal(t1.getComments(), t2.getComments())) return false;
    if (!Comparing.equal(t1.getIcon(), t2.getIcon())) return false;
    if (!Comparing.equal(t1.getCustomIcon(), t2.getCustomIcon())) return false;
    return Comparing.equal(t1.getRepository(), t2.getRepository());
  }

  public static boolean tasksEqual(@NotNull List<? extends Task> tasks1, @NotNull List<? extends Task> tasks2) {
    if (tasks1.size() != tasks2.size()) return false;
    for (int i = 0; i < tasks1.size(); i++) {
      if (!tasksEqual(tasks1.get(i), tasks2.get(i))) {
        return false;
      }
    }
    return true;
  }

  public static boolean tasksEqual(@NotNull Task[] task1, @NotNull Task[] task2) {
    return tasksEqual(Arrays.asList(task1), Arrays.asList(task2));
  }

  /**
   * Print pretty-formatted XML to {@code logger} if its level is DEBUG or below
   */
  public static void prettyFormatXmlToLog(@NotNull Logger logger, @NotNull Element element) {
    if (logger.isDebugEnabled()) {
      // alternatively
      //new XMLOutputter(Format.getPrettyFormat()).outputString(root)
      logger.debug("\n" + JDOMUtil.createOutputter("\n").outputString(element));
    }
  }

  /**
   * Parse and print pretty-formatted XML to {@code logger} if its level is DEBUG or below
   */
  public static void prettyFormatXmlToLog(@NotNull Logger logger, @NotNull InputStream xml) {
    if (logger.isDebugEnabled()) {
      try {
        logger.debug("\n" + JDOMUtil.createOutputter("\n").outputString(JDOMUtil.loadDocument(xml)));
      }
      catch (Exception e) {
        logger.debug(e);
      }
    }
  }

  /**
   * Parse and print pretty-formatted XML to {@code logger} if its level is DEBUG or below
   */
  public static void prettyFormatXmlToLog(@NotNull Logger logger, @NotNull String xml) {
    if (logger.isDebugEnabled()) {
      try {
        logger.debug("\n" + JDOMUtil.createOutputter("\n").outputString(JDOMUtil.loadDocument(xml)));
      }
      catch (Exception e) {
        logger.debug(e);
      }
    }
  }

  /**
   * Parse and print pretty-formatted Json to {@code logger} if its level is DEBUG or below
   */
  public static void prettyFormatJsonToLog(@NotNull Logger logger, @NotNull String json) {
    if (logger.isDebugEnabled()) {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      logger.debug("\n" + gson.toJson(gson.fromJson(json, JsonElement.class)));
    }
  }
}
