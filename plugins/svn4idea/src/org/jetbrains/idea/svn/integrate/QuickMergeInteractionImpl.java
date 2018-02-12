// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.IntersectingLocalChangesPanel;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.List;

import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;
import static com.intellij.openapi.ui.Messages.*;
import static com.intellij.util.Functions.TO_STRING;
import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.map2Array;
import static org.jetbrains.idea.svn.integrate.LocalChangesAction.*;
import static org.jetbrains.idea.svn.integrate.ToBeMergedDialog.MERGE_ALL_CODE;

public class QuickMergeInteractionImpl implements QuickMergeInteraction {

  @NotNull private final MergeContext myMergeContext;
  @NotNull private final Project myProject;
  @NotNull private final String myTitle;

  public QuickMergeInteractionImpl(@NotNull MergeContext mergeContext) {
    myMergeContext = mergeContext;
    myProject = mergeContext.getProject();
    myTitle = mergeContext.getTitle();
  }

  @NotNull
  @Override
  public QuickMergeContentsVariants selectMergeVariant() {
    QuickMergeWayOptionsPanel panel = new QuickMergeWayOptionsPanel();
    DialogBuilder builder = new DialogBuilder(myProject);

    builder.title("Select Merge Variant").centerPanel(panel.getMainPanel()).removeAllActions();
    panel.setWrapper(builder.getDialogWrapper());
    builder.show();

    return panel.getVariant();
  }

  @Override
  public boolean shouldContinueSwitchedRootFound() {
    return prompt("There are some switched paths in the working copy. Do you want to continue?");
  }

  @Override
  public boolean shouldReintegrate(@NotNull String targetUrl) {
    return prompt("<html><body>You are going to reintegrate changes.<br><br>This will make branch '" +
                  myMergeContext.getSourceUrl().toDecodedString() +
                  "' <b>no longer usable for further work</b>." +
                  "<br>It will not be able to correctly absorb new trunk (" + targetUrl +
                  ") changes,<br>nor can this branch be properly reintegrated to trunk again.<br><br>Are you sure?</body></html>");
  }

  @NotNull
  @Override
  public SelectMergeItemsResult selectMergeItems(@NotNull List<SvnChangeList> lists,
                                                 @NotNull MergeChecker mergeChecker,
                                                 boolean allStatusesCalculated,
                                                 boolean allListsLoaded) {
    ToBeMergedDialog dialog =
      new ToBeMergedDialog(myMergeContext, lists, myMergeContext.getTitle(), mergeChecker, allStatusesCalculated, allListsLoaded);
    dialog.show();

    QuickMergeContentsVariants resultCode = toMergeVariant(dialog.getExitCode());
    List<SvnChangeList> selectedLists = resultCode == QuickMergeContentsVariants.select ? dialog.getSelected() : emptyList();

    return new SelectMergeItemsResult(resultCode, selectedLists);
  }

  @NotNull
  @Override
  public LocalChangesAction selectLocalChangesAction(boolean mergeAll) {
    LocalChangesAction[] possibleResults;
    String message;

    if (!mergeAll) {
      possibleResults = new LocalChangesAction[]{shelve, inspect, continueMerge, cancel};
      message = "There are local changes that will intersect with merge changes.\nDo you want to continue?";
    } else {
      possibleResults = new LocalChangesAction[]{shelve, continueMerge, cancel};
      message = "There are local changes that can potentially intersect with merge changes.\nDo you want to continue?";
    }

    int result = showDialog(message, myTitle, map2Array(possibleResults, String.class, TO_STRING()), 0, getQuestionIcon());
    return result == -1 ? cancel : possibleResults[result];
  }

  @Override
  public void showIntersectedLocalPaths(@NotNull List<FilePath> paths) {
    IntersectingLocalChangesPanel.showInVersionControlToolWindow(myProject, myTitle + ", local changes intersection",
      paths, "The following file(s) have local changes that will intersect with merge changes:");
  }

  @Override
  public void showErrors(@NotNull String message, @NotNull List<VcsException> exceptions) {
    AbstractVcsHelper.getInstance(myProject).showErrors(exceptions, message);
  }

  @Override
  public void showErrors(@NotNull String message, boolean isError) {
    VcsBalloonProblemNotifier.showOverChangesView(myProject, message, isError ? MessageType.ERROR : MessageType.WARNING);
  }

  private boolean prompt(@NotNull String question) {
    return showOkCancelDialog(myProject, question, myTitle, getQuestionIcon()) == OK;
  }

  @NotNull
  private static QuickMergeContentsVariants toMergeVariant(int exitCode) {
    switch (exitCode) {
      case MERGE_ALL_CODE:
        return QuickMergeContentsVariants.all;
      case OK_EXIT_CODE:
        return QuickMergeContentsVariants.select;
      default:
        return QuickMergeContentsVariants.cancel;
    }
  }
}
