// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;
import java.util.HashSet;

/**
 * Command runs 'Inspect code' action.
 * Run 'Inspect code' command in context defined by parameters.
 * <p>
 * Syntax: %inspectCode [extension]
 * Example: %inspectCode kt
 */
public class InspectionCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "inspectCode";

  public InspectionCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    String extension = extractCommandArgument(PREFIX);

    @NotNull Project project = context.getProject();
    DumbService.getInstance(project).smartInvokeLater(() -> {
      InspectionManagerEx inspectionManagerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
      //noinspection TestOnlyProblems
      GlobalInspectionContextImpl inspectionContext = new GlobalInspectionContextImpl(project, inspectionManagerEx.getContentManager()) {
        @Override
        protected void notifyInspectionsFinished(@NotNull AnalysisScope scope) {
          super.notifyInspectionsFinished(scope);
          context.message(PerformanceTestingBundle.message("command.inspection.finish"), getLine());
          actionCallback.setDone();
        }
      };

      //set up list of files
      AnalysisScope scope = getAnalysisScope(extension, project);
      if (scope == null) {
        context.message(PerformanceTestingBundle.message("command.inspection.extension") + " " + extension, getLine());
      }
      else {
        inspectionContext.doInspections(scope);
      }
    });

    return Promises.toPromise(actionCallback);
  }

  public static @Nullable AnalysisScope getAnalysisScope(String extension, @NotNull Project project) {
    AnalysisScope scope;
    if (extension.isEmpty()) {
      scope = new AnalysisScope(project);
    }
    else {
      Collection<VirtualFile> files = getFiles(extension, project);
      if (files.isEmpty()) {
        return null;
      }
      scope = new AnalysisScope(project, getFiles(extension, project));
    }
    return scope;
  }

  private static @NotNull Collection<VirtualFile> getFiles(final @NotNull String extension, @NotNull Project project) {
    final Collection<VirtualFile> files = new HashSet<>(100);
    FileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    index.iterateContent(fileOrDir -> {
      if (StringUtil.equals(fileOrDir.getExtension(), extension)) {
        files.add(fileOrDir);
      }
      return true;
    });
    return files;
  }
}