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
package com.intellij.tasks.actions;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
* @author Dmitry Avdeev
*/
public class TaskAutoCompletionListProvider extends TextFieldWithAutoCompletionListProvider<Task> {

  private final Project myProject;

  public TaskAutoCompletionListProvider(Project project) {
    super(null);
    myProject = project;
  }

  @Override
  protected String getQuickDocHotKeyAdvertisementTail(@NotNull String shortcut) {
    return "task description and comments";
  }

  @NotNull
  @Override
  public List<Task> getItems(final String prefix, final boolean cached, CompletionParameters parameters) {
    return TaskSearchSupport.getItems(TaskManager.getManager(myProject), prefix, cached, parameters.isAutoPopup());
  }

  @Override
  public void setItems(@Nullable Collection variants) {
    // Do nothing
  }

  @Override
  public LookupElementBuilder createLookupBuilder(@NotNull final Task task) {
    LookupElementBuilder builder = super.createLookupBuilder(task);

    builder = builder.withLookupString(task.getSummary());
    if (task.isClosed()) {
      builder = builder.strikeout();
    }

    return builder;
  }

  @Override
  protected InsertHandler<LookupElement> createInsertHandler(@NotNull final Task task) {
    return new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        Document document = context.getEditor().getDocument();
        String s = ((TaskManagerImpl)TaskManager.getManager(context.getProject())).getChangelistName(task);
        s = StringUtil.convertLineSeparators(s);
        document.replaceString(context.getStartOffset(), context.getTailOffset(), s);
        context.getEditor().getCaretModel().moveToOffset(context.getStartOffset() + s.length());

        TaskAutoCompletionListProvider.this.handleInsert(task);
      }
    };
  }

  protected void handleInsert(@NotNull final Task task) {
    // Override it for autocompletion insert handler
  }

  @Override
  protected Icon getIcon(@NotNull final Task task) {
    return task.getIcon();
  }

  @NotNull
  @Override
  protected String getLookupString(@NotNull final Task task) {
    return task.getPresentableId();
  }

  @Override
  protected String getTailText(@NotNull final Task task) {
    return " " + task.getSummary();
  }

  @Override
  protected String getTypeText(@NotNull final Task task) {
    return null;
  }

  @Override
  public int compare(@NotNull final Task task1, @NotNull final Task task2) {
    // N/A here
    throw new UnsupportedOperationException();
  }
}
