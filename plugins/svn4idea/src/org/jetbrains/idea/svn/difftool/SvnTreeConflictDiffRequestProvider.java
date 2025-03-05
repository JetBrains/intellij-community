// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.difftool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProvider;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.ConflictedSvnChange;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.treeConflict.TreeConflictRefreshablePanel;

import javax.swing.*;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnTreeConflictDiffRequestProvider implements ChangeDiffRequestProvider {
  @Override
  public @NotNull ThreeState isEquals(@NotNull Change change1, @NotNull Change change2) {
    if (change1 instanceof ConflictedSvnChange conflict1 && change2 instanceof ConflictedSvnChange conflict2) {

      if (!conflict1.isTreeConflict() && !conflict2.isTreeConflict()) return ThreeState.UNSURE;
      if (!conflict1.isTreeConflict() || !conflict2.isTreeConflict()) return ThreeState.NO;

      TreeConflictDescription description1 = conflict1.getBeforeDescription();
      TreeConflictDescription description2 = conflict2.getBeforeDescription();
      return TreeConflictRefreshablePanel.descriptionsEqual(description1, description2) ? ThreeState.YES : ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@Nullable Project project, @NotNull Change change) {
    return change instanceof ConflictedSvnChange && ((ConflictedSvnChange)change).getConflictState().isTree();
  }

  @Override
  public @NotNull DiffRequest process(@NotNull ChangeDiffRequestProducer presentable,
                                      @NotNull UserDataHolder context,
                                      @NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    return new SvnTreeConflictDiffRequest(((ConflictedSvnChange)presentable.getChange()));
  }

  public static class SvnTreeConflictDiffRequest extends DiffRequest {
    private final @NotNull ConflictedSvnChange myChange;

    public SvnTreeConflictDiffRequest(@NotNull ConflictedSvnChange change) {
      myChange = change;
    }

    public @NotNull ConflictedSvnChange getChange() {
      return myChange;
    }

    @Override
    public @Nullable String getTitle() {
      return ChangeDiffRequestProducer.getRequestTitle(myChange);
    }
  }

  public static class SvnTreeConflictDiffTool implements FrameDiffTool {
    @Override
    public @NotNull String getName() {
      return message("svn.tree.conflict.viewer");
    }

    @Override
    public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return request instanceof SvnTreeConflictDiffRequest;
    }

    @Override
    public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return new SvnTreeConflictDiffViewer(context, (SvnTreeConflictDiffRequest)request);
    }
  }

  private static class SvnTreeConflictDiffViewer implements FrameDiffTool.DiffViewer {
    private final @NotNull DiffContext myContext;
    private final @NotNull SvnTreeConflictDiffRequest myRequest;
    private final @NotNull Wrapper myPanel = new Wrapper();

    private final @NotNull BackgroundTaskQueue myQueue;
    private final @NotNull TreeConflictRefreshablePanel myDelegate;

    SvnTreeConflictDiffViewer(@NotNull DiffContext context, @NotNull SvnTreeConflictDiffRequest request) {
      myContext = context;
      myRequest = request;

      myQueue = new BackgroundTaskQueue(myContext.getProject(), message("progress.title.loading.change.details"));

      // We don't need to listen on File/Document, because panel always will be the same for a single change.
      // And if Change will change - we'll create new DiffRequest and DiffViewer
      myDelegate = new TreeConflictRefreshablePanel(myContext.getProject(), myQueue, myRequest.getChange());
      myDelegate.refresh();
      myPanel.setContent(myDelegate.getPanel());
    }

    @Override
    public @NotNull JComponent getComponent() {
      return myPanel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myPanel;
    }

    @Override
    public @NotNull FrameDiffTool.ToolbarComponents init() {
      return new FrameDiffTool.ToolbarComponents();
    }

    @Override
    public void dispose() {
      myQueue.clear();
      Disposer.dispose(myDelegate);
    }
  }
}
