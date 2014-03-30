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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.*;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;

import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 3/27/13
 * Time: 11:40 AM
 */
public class QuickMergeInteractionImpl implements QuickMergeInteraction {
  private final Project myProject;
  private String myTitle;

  public QuickMergeInteractionImpl(Project project) {
    myProject = project;
  }

  @Override
  public void setTitle(@NotNull String title) {
    myTitle = title;
  }

  @Override
  public QuickMergeContentsVariants selectMergeVariant() {
    final QuickMergeWayOptionsPanel panel = new QuickMergeWayOptionsPanel();
    final DialogBuilder builder = new DialogBuilder(myProject);
    builder.removeAllActions();
    builder.setTitle("Select Merge Variant");
    builder.setCenterPanel(panel.getMainPanel());
    panel.setWrapper(builder.getDialogWrapper());
    builder.show();

    return panel.getVariant();
  }

  @Override
  public boolean shouldContinueSwitchedRootFound() {
    return prompt("There are some switched paths in the working copy. Do you want to continue?");
  }

  @Override
  public boolean shouldReintegrate(@NotNull final String sourceUrl, @NotNull final String targetUrl) {
    return prompt("<html><body>You are going to reintegrate changes.<br><br>This will make branch '" + sourceUrl +
                                       "' <b>no longer usable for further work</b>." +
                                       "<br>It will not be able to correctly absorb new trunk (" + targetUrl +
                                       ") changes,<br>nor can this branch be properly reintegrated to trunk again.<br><br>Are you sure?</body></html>");
  }

  @NotNull
  @Override
  public SelectMergeItemsResult selectMergeItems(List<CommittedChangeList> lists, String mergeTitle, MergeChecker mergeChecker) {
    final ToBeMergedDialog dialog = new ToBeMergedDialog(myProject, lists, mergeTitle, mergeChecker, null);
    dialog.show();
    return new SelectMergeItemsResult() {
      @Override
      public QuickMergeContentsVariants getResultCode() {
        final int code = dialog.getExitCode();
        if (ToBeMergedDialog.MERGE_ALL_CODE == code) {
          return QuickMergeContentsVariants.all;
        }
        return DialogWrapper.OK_EXIT_CODE == code ? QuickMergeContentsVariants.select : QuickMergeContentsVariants.cancel;
      }

      @Override
      public List<CommittedChangeList> getSelectedLists() {
        return dialog.getSelected();
      }
    };
  }

  @NotNull
  @Override
  public LocalChangesAction selectLocalChangesAction(final boolean mergeAll) {
    if (! mergeAll) {
      final LocalChangesAction[] possibleResults = {LocalChangesAction.shelve, LocalChangesAction.inspect,
        LocalChangesAction.continueMerge, LocalChangesAction.cancel};
      final int result = Messages.showDialog("There are local changes that will intersect with merge changes.\nDo you want to continue?", myTitle,
                                                 new String[]{"Shelve local changes", "Inspect changes", "Continue merge", "Cancel"},
                                                  0, Messages.getQuestionIcon());
      return possibleResults[result];
    } else {
      final LocalChangesAction[] possibleResults = {LocalChangesAction.shelve, LocalChangesAction.continueMerge, LocalChangesAction.cancel};
      final int result = Messages.showDialog("There are local changes that can potentially intersect with merge changes.\nDo you want to continue?", myTitle,
                                                   new String[]{"Shelve local changes", "Continue merge", "Cancel"},
                                                    0, Messages.getQuestionIcon());
      return possibleResults[result];
    }
  }

  @Override
  public void showIntersectedLocalPaths(final List<FilePath> paths) {
    IntersectingLocalChangesPanel.showInVersionControlToolWindow(myProject, myTitle + ", local changes intersection",
      paths, "The following file(s) have local changes that will intersect with merge changes:");
  }

  @Override
  public void showError(@NotNull Exception exception) {
    AbstractVcsHelper.getInstance(myProject).showErrors(Collections.singletonList(new VcsException(exception)),
      exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage());
  }

  @Override
  public void showErrors(String message, List<VcsException> exceptions) {
    AbstractVcsHelper.getInstance(myProject).showErrors(exceptions, message);
  }

  @Override
  public void showErrors(String message, boolean isError) {
    VcsBalloonProblemNotifier.showOverChangesView(myProject, message, isError ? MessageType.ERROR : MessageType.WARNING);
  }

  @Override
  public List<CommittedChangeList> showRecentListsForSelection(@NotNull List<CommittedChangeList> list,
                                                               @NotNull String mergeTitle,
                                                               @NotNull MergeChecker mergeChecker,
                                                               @NotNull PairConsumer<Long, MergeDialogI> loader,
                                                               boolean everyThingLoaded) {
    final ToBeMergedDialog dialog = new ToBeMergedDialog(myProject, list, mergeTitle, mergeChecker, loader);
    if (everyThingLoaded) {
      dialog.setEverythingLoaded(true);
    }
    dialog.show();
    if (DialogWrapper.OK_EXIT_CODE == dialog.getExitCode()) {
      return dialog.getSelected();
    }
    return null;
  }

  private boolean prompt(final String question) {
    return Messages.showOkCancelDialog(myProject, question, myTitle, Messages.getQuestionIcon()) == Messages.OK;
  }
}
