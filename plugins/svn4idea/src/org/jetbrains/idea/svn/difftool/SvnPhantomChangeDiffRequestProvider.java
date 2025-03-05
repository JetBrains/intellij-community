// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.difftool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProvider;
import com.intellij.util.ThreeState;
import com.intellij.vcsUtil.UIVcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.ConflictedSvnChange;

import javax.swing.*;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnPhantomChangeDiffRequestProvider implements ChangeDiffRequestProvider {
  @Override
  public @NotNull ThreeState isEquals(@NotNull Change change1, @NotNull Change change2) {
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@Nullable Project project, @NotNull Change change) {
    return change instanceof ConflictedSvnChange && ((ConflictedSvnChange)change).isPhantom();
  }

  @Override
  public @NotNull DiffRequest process(@NotNull ChangeDiffRequestProducer presentable,
                                      @NotNull UserDataHolder context,
                                      @NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    indicator.checkCanceled();
    return new SvnPhantomDiffRequest(presentable.getChange());
  }

  public static class SvnPhantomDiffRequest extends DiffRequest {
    private final @NotNull Change myChange;

    public SvnPhantomDiffRequest(@NotNull Change change) {
      myChange = change;
    }

    @Override
    public @Nullable String getTitle() {
      return ChangeDiffRequestProducer.getRequestTitle(myChange);
    }
  }

  public static class SvnPhantomDiffTool implements FrameDiffTool {
    @Override
    public @NotNull String getName() {
      return message("svn.phantom.changes.viewer");
    }

    @Override
    public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return request instanceof SvnPhantomDiffRequest;
    }

    @Override
    public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return new DiffViewer() {
        @Override
        public @NotNull JComponent getComponent() {
          return UIVcsUtil.infoPanel(message("label.svn.phantom.change"), message("text.svn.phantom.change"));
        }

        @Override
        public @Nullable JComponent getPreferredFocusedComponent() {
          return null;
        }

        @Override
        public @NotNull ToolbarComponents init() {
          return new ToolbarComponents();
        }

        @Override
        public void dispose() {
        }
      };
    }
  }
}
