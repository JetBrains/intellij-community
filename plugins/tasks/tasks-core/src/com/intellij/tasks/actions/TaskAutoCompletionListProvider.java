// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.tasks.core.TaskSymbol;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

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

  @Override
  public @NotNull List<Task> getItems(final String prefix, final boolean cached, CompletionParameters parameters) {
    return TaskSearchSupport.getItems(TaskManager.getManager(myProject), prefix, cached, parameters.isAutoPopup())
      .stream()
      .sorted(Comparator.comparingInt(t -> {
        return switch (t.getState()) {
          case IN_PROGRESS -> 0;
          case REOPENED -> 1;
          case SUBMITTED, OPEN -> 2;
          case OTHER -> 3;
          case null, default -> 4;
        };
      }))
      .toList();
  }

  @Override
  public void setItems(@Nullable Collection variants) {
    // Do nothing
  }

  @Override
  public @NotNull LookupElementBuilder createLookupBuilder(final @NotNull Task task) {
    LookupElementBuilder builder =
      LookupElementBuilder.createWithSymbolPointer(getLookupString(task), new TaskSymbol(task))
        .withIcon(task.getIcon())
        .withInsertHandler(createInsertHandler(task))
        .withTailText(" " + task.getSummary(), true)
        .withLookupString(task.getSummary());
    if (task.isClosed()) {
      builder = builder.strikeout();
    }
    return builder;
  }

  @Override
  protected InsertHandler<LookupElement> createInsertHandler(final @NotNull Task task) {
    return new InsertHandler<>() {
      @Override
      public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        Document document = context.getEditor().getDocument();
        String s = ((TaskManagerImpl)TaskManager.getManager(context.getProject())).getChangelistName(task);
        s = StringUtil.convertLineSeparators(s);
        document.replaceString(context.getStartOffset(), context.getTailOffset(), s);
        context.getEditor().getCaretModel().moveToOffset(context.getStartOffset() + s.length());

        TaskAutoCompletionListProvider.this.handleInsert(task);
      }
    };
  }

  protected void handleInsert(final @NotNull Task task) {
    // Override it for autocompletion insert handler
  }

  @Override
  protected @NotNull String getLookupString(@NotNull Task task) {
    return task.getPresentableId();
  }

  @Override
  public int compare(final @NotNull Task task1, final @NotNull Task task2) {
    // N/A here
    throw new UnsupportedOperationException();
  }
}
