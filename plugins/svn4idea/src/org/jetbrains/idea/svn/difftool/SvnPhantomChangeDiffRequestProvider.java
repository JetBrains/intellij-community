package org.jetbrains.idea.svn.difftool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.chains.DiffRequestProducerException;
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

public class SvnPhantomChangeDiffRequestProvider implements ChangeDiffRequestProvider {
  @NotNull
  @Override
  public ThreeState isEquals(@NotNull Change change1, @NotNull Change change2) {
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@Nullable Project project, @NotNull Change change) {
    return change instanceof ConflictedSvnChange && ((ConflictedSvnChange)change).isPhantom();
  }

  @NotNull
  @Override
  public DiffRequest process(@NotNull ChangeDiffRequestProducer presentable,
                             @NotNull UserDataHolder context,
                             @NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    indicator.checkCanceled();
    return new SvnPhantomDiffRequest(presentable.getChange());
  }

  public static class SvnPhantomDiffRequest extends DiffRequest {
    @NotNull private final Change myChange;

    public SvnPhantomDiffRequest(@NotNull Change change) {
      myChange = change;
    }

    @Nullable
    @Override
    public String getTitle() {
      return ChangeDiffRequestProducer.getRequestTitle(myChange);
    }
  }

  public static class SvnPhantomDiffTool implements FrameDiffTool {
    @NotNull
    @Override
    public String getName() {
      return "SVN phantom changes viewer";
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
