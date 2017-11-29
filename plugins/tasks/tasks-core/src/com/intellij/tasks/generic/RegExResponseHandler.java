/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.tasks.generic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.xmlb.annotations.Tag;
import org.intellij.lang.regexp.RegExpLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler that uses legacy regex-based approach for tasks extraction.
 *
 * @author Evgeny.Zakrevsky
 * @author Mikhail Golubev
 */
@Tag("RegExResponseHandler")
public final class RegExResponseHandler extends ResponseHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.generic.RegExResponseHandler");
  private static final String ID_PLACEHOLDER = "{id}";
  private static final String SUMMARY_PLACEHOLDER = "{summary}";

  private String myTaskRegex = "";

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public RegExResponseHandler() {
    // empty
  }

  public RegExResponseHandler(GenericRepository repository) {
    super(repository);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RegExResponseHandler handler = (RegExResponseHandler)o;
    return myTaskRegex.equals(handler.myTaskRegex);
  }

  @Override
  public int hashCode() {
    return myTaskRegex.hashCode();
  }

  @NotNull
  @Override
  public JComponent getConfigurationComponent(@NotNull Project project) {
    FormBuilder builder = FormBuilder.createFormBuilder();
    final EditorTextField taskPatternText;
    taskPatternText = new LanguageTextField(RegExpLanguage.INSTANCE, project, myTaskRegex, false);
    taskPatternText.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        myTaskRegex = taskPatternText.getText();
      }
    });
    String tooltip = "<html>Task pattern should be a regexp with two matching groups: ({id}.+?) and ({summary}.+?)";
    builder.addLabeledComponent("Task Pattern:", new JBScrollPane(taskPatternText)).addTooltip(tooltip);
    return builder.getPanel();
  }

  @NotNull
  @Override
  public Task[] parseIssues(@NotNull String response, int max) throws Exception {
    final List<String> placeholders = getPlaceholders(myTaskRegex);
    if (!placeholders.contains(ID_PLACEHOLDER) || !placeholders.contains(SUMMARY_PLACEHOLDER)) {
      throw new Exception("Incorrect Task Pattern");
    }

    final String taskPatternWithoutPlaceholders = myTaskRegex.replaceAll("\\{.+?\\}", "");
    Matcher matcher = Pattern
      .compile(taskPatternWithoutPlaceholders,
               Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNICODE_CASE | Pattern.CANON_EQ)
      .matcher(response);

    List<Task> tasks = new ArrayList<>();
    for (int i = 0; i < max && matcher.find(); i++) {
      String id = matcher.group(placeholders.indexOf(ID_PLACEHOLDER) + 1);
      String summary = matcher.group(placeholders.indexOf(SUMMARY_PLACEHOLDER) + 1);
      tasks.add(new GenericTask(id, summary, myRepository));
    }
    return tasks.toArray(new Task[tasks.size()]);
  }

  @Nullable
  @Override
  public Task parseIssue(@NotNull String response) throws Exception {
    return null;
  }

  private static List<String> getPlaceholders(String value) {
    if (value == null) {
      return ContainerUtil.emptyList();
    }

    List<String> vars = new ArrayList<>();
    Matcher m = Pattern.compile("\\{(.+?)\\}").matcher(value);
    while (m.find()) {
      vars.add(m.group(0));
    }
    return vars;
  }

  public String getTaskRegex() {
    return myTaskRegex;
  }

  public void setTaskRegex(String taskRegex) {
    myTaskRegex = taskRegex;
  }

  @Override
  public boolean isConfigured() {
    return !StringUtil.isEmpty(myTaskRegex);
  }

  @NotNull
  @Override
  public ResponseType getResponseType() {
    return ResponseType.TEXT;
  }

  @Override
  public RegExResponseHandler clone() {
    RegExResponseHandler clone = (RegExResponseHandler)super.clone();
    clone.myTaskRegex = myTaskRegex;
    return clone;
  }
}
