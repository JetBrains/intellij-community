// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.dialogs.IntersectingLocalChangesPanel;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.List;

import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;
import static com.intellij.openapi.ui.Messages.*;
import static com.intellij.util.Functions.TO_STRING;
import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.map2Array;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.integrate.LocalChangesAction.*;
import static org.jetbrains.idea.svn.integrate.ToBeMergedDialog.MERGE_ALL_CODE;

public class QuickMergeInteractionImpl implements QuickMergeInteraction {

  @NotNull private final MergeContext myMergeContext;
  @NotNull private final Project myProject;

  public QuickMergeInteractionImpl(@NotNull MergeContext mergeContext) {
    myMergeContext = mergeContext;
    myProject = mergeContext.getProject();
  }

  @NotNull
  @Override
  public QuickMergeContentsVariants selectMergeVariant() {
    QuickMergeWayOptionsPanel panel = new QuickMergeWayOptionsPanel();
    DialogBuilder builder = new DialogBuilder(myProject);

    builder.title(message("dialog.title.select.merge.variant")).centerPanel(panel.getMainPanel()).removeAllActions();
    panel.setWrapper(builder.getDialogWrapper());
    builder.show();

    return panel.getVariant();
  }

  @Override
  public boolean shouldContinueSwitchedRootFound() {
    return prompt(message("dialog.message.merge.with.switched.paths.in.working.copy"));
  }

  @Override
  public boolean shouldReintegrate(@NotNull Url targetUrl) {
    return prompt(wrapInHtml(
      message("dialog.message.merge.confirm.reintegrate", myMergeContext.getSourceUrl().toDecodedString(), targetUrl.toDecodedString())));
  }

  @NotNull
  @Override
  public SelectMergeItemsResult selectMergeItems(@NotNull List<SvnChangeList> lists,
                                                 @NotNull MergeChecker mergeChecker,
                                                 boolean allStatusesCalculated,
                                                 boolean allListsLoaded) {
    ToBeMergedDialog dialog = new ToBeMergedDialog(myMergeContext, lists, mergeChecker, allStatusesCalculated, allListsLoaded);
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
      message = message("dialog.message.merge.intersects.with.local.changes.prompt");
    }
    else {
      possibleResults = new LocalChangesAction[]{shelve, continueMerge, cancel};
      message = message("dialog.message.merge.potentially.intersects.with.local.changes.prompt");
    }

    int result =
      showDialog(message, myMergeContext.getMergeTitle(), map2Array(possibleResults, String.class, TO_STRING()), 0, getQuestionIcon());
    return result == -1 ? cancel : possibleResults[result];
  }

  @Override
  public void showIntersectedLocalPaths(@NotNull List<FilePath> paths) {
    IntersectingLocalChangesPanel.showInVersionControlToolWindow(
      myProject,
      message("tab.title.merge.local.changes.intersection", myMergeContext.getMergeTitle()),
      paths
    );
  }

  @Override
  public void showErrors(@TabTitle @NotNull String message, @NotNull List<VcsException> exceptions) {
    AbstractVcsHelper.getInstance(myProject).showErrors(exceptions, message);
  }

  @Override
  public void showErrors(@NotificationContent @NotNull String message, boolean isError) {
    VcsBalloonProblemNotifier.showOverChangesView(myProject, message, isError ? MessageType.ERROR : MessageType.WARNING);
  }

  private boolean prompt(@DialogMessage @NotNull String question) {
    return showOkCancelDialog(myProject, question, myMergeContext.getMergeTitle(), getQuestionIcon()) == OK;
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
