package org.jetbrains.idea.svn.difftool;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProvider;
import com.intellij.util.ThreeState;
import com.intellij.vcsUtil.UIVcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SvnPhantomChangeDiffRequestProvider implements ChangeDiffRequestProvider {
  @NotNull
  @Override
  public ThreeState isEquals(@NotNull Change change1, @NotNull Change change2) {
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@NotNull Project project, @NotNull Change change) {
    return change.isPhantom();
  }

  @NotNull
  @Override
  public DiffRequest process(@NotNull ChangeDiffRequestPresentable presentable,
                             @NotNull UserDataHolder context,
                             @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException, ProcessCanceledException {
    indicator.checkCanceled();
    return new SvnPhantomDiffRequest(presentable.getChange());
  }

  public static class SvnPhantomDiffRequest extends UserDataHolderBase implements DiffRequest {
    @NotNull private final Change myChange;

    public SvnPhantomDiffRequest(@NotNull Change change) {
      myChange = change;
    }

    @Nullable
    @Override
    public String getTitle() {
      return ChangeDiffRequestPresentable.getRequestTitle(myChange);
    }

    @Override
    public void onAssigned(boolean isAssigned) {
    }
  }

  public static class SvnPhantomDiffTool implements FrameDiffTool {
    @NotNull
    @Override
    public String getName() {
      return "SVN Phantom Changes Viewer";
    }

    @Override
    public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return request instanceof SvnPhantomDiffRequest;
    }

    @NotNull
    @Override
    public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
      return new DiffViewer() {
        @NotNull
        @Override
        public JComponent getComponent() {
          return UIVcsUtil.infoPanel("Technical record",
                                     "This change is recorded because its target file was deleted,\nand some parent directory was copied (or moved) into the new place.");
        }

        @Nullable
        @Override
        public JComponent getPreferredFocusedComponent() {
          return null;
        }

        @NotNull
        @Override
        public ToolbarComponents init() {
          return new ToolbarComponents();
        }

        @Override
        public void dispose() {
        }
      };
    }
  }
}
