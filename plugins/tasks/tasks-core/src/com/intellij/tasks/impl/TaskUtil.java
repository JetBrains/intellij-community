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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dmitry Avdeev
 */
public class TaskUtil {
  private static SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
  // Almost ISO-8601 strict except date parts may be separated by '/' as well
  private static Pattern ISO8601_DATE_PATTERN = Pattern.compile(
    "(\\d{4}[/-]\\d{2}[/-]\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})(.\\d{3,})?([+-]\\d{2}:\\d{2}|[+-]\\d{4}|[+-]\\d{2}|Z)?");

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
    } else {
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
    String milliseconds = m.group(3);
    milliseconds = milliseconds == null? "000" : milliseconds.substring(1, 4);
    String timezone = m.group(4);
    if (timezone == null || timezone.equals("Z")) {
      timezone = "+0000";
    } else if (timezone.length() == 3) {
      // [+-]HH
      timezone += "00";
    } else if (timezone.length() == 6) {
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
}
