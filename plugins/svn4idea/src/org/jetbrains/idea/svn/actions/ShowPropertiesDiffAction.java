/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.actions;

import com.intellij.diff.DiffManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBaseContentRevision;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffRequest;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffRequest.PropertyContent;
import org.jetbrains.idea.svn.history.SvnRepositoryContentRevision;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.exists;

public class ShowPropertiesDiffAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isVisible = checkThatChangesAreUnderSvn(e.getData(VcsDataKeys.CHANGES));

    e.getPresentation().setVisible(isVisible);
    e.getPresentation().setEnabled(isVisible && e.getProject() != null);
  }

  private static boolean checkThatChangesAreUnderSvn(@Nullable Change[] changes) {
    return changes != null && exists(changes, change -> isUnderSvn(change.getBeforeRevision()) || isUnderSvn(change.getAfterRevision()));
  }

  private static boolean isUnderSvn(@Nullable ContentRevision revision) {
    return revision instanceof SvnBaseContentRevision;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGE_LEAD_SELECTION);

    if (checkThatChangesAreUnderSvn(changes)) {
      new CalculateAndShow(e.getRequiredData(PROJECT), changes[0], e.getPresentation().getText()).queue();
    }
  }

  private static class CalculateAndShow extends Task.Backgroundable {
    private final Change myChange;
    private List<PropertyData> myBeforeContent;
    private List<PropertyData> myAfterContent;
    private SVNRevision myBeforeRevisionValue;
    private SVNRevision myAfterRevision;
    private Exception myException;
    private final String myErrorTitle;

    private CalculateAndShow(@NotNull Project project, final Change change, final String errorTitle) {
      super(project, SvnBundle.message("fetching.properties.contents.progress.title"), true, PerformInBackgroundOption.DEAF);
      myChange = change;
      myErrorTitle = errorTitle;
    }

    public void run(@NotNull final ProgressIndicator indicator) {
      final SvnVcs vcs = SvnVcs.getInstance(myProject);

      try {
        myBeforeRevisionValue = getBeforeRevisionValue(myChange);
        myAfterRevision = getAfterRevisionValue(myChange);

        myBeforeContent = getPropertyList(vcs, myChange.getBeforeRevision(), myBeforeRevisionValue);
        indicator.checkCanceled();
        // gets exactly WORKING revision property
        myAfterContent = getPropertyList(vcs, myChange.getAfterRevision(), myAfterRevision);
      }
      catch (SVNException | VcsException exc) {
        myException = exc;
      }
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        Messages.showErrorDialog(myException.getMessage(), myErrorTitle);
        return;
      }
      if (myBeforeContent != null && myAfterContent != null && myBeforeRevisionValue != null && myAfterRevision != null) {
        SvnPropertiesDiffRequest diffRequest;
        if (compareRevisions(myBeforeRevisionValue, myAfterRevision) > 0) {
          diffRequest = new SvnPropertiesDiffRequest(getDiffWindowTitle(myChange),
                                                     new PropertyContent(myAfterContent), new PropertyContent(myBeforeContent),
                                                     revisionToString(myAfterRevision), revisionToString(myBeforeRevisionValue));
        }
        else {
          diffRequest = new SvnPropertiesDiffRequest(getDiffWindowTitle(myChange),
                                                     new PropertyContent(myBeforeContent), new PropertyContent(myAfterContent),
                                                     revisionToString(myBeforeRevisionValue), revisionToString(myAfterRevision));
        }
        DiffManager.getInstance().showDiff(myProject, diffRequest);
      }
    }
  }

  @NotNull
  private static SVNRevision getBeforeRevisionValue(@NotNull Change change) {
    ContentRevision beforeRevision = change.getBeforeRevision();
    if (beforeRevision != null) {
      return ((SvnRevisionNumber)beforeRevision.getRevisionNumber()).getRevision();
    }
    else {
      return SVNRevision.create(((SvnRevisionNumber)notNull(change.getAfterRevision()).getRevisionNumber()).getRevision().getNumber() - 1);
    }
  }

  @NotNull
  private static SVNRevision getAfterRevisionValue(@NotNull Change change) {
    ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision != null) {
      // CurrentContentRevision will be here, for instance, if invoked from changes dialog for "Compare with Branch" action
      return afterRevision instanceof CurrentContentRevision
             ? SVNRevision.WORKING
             : ((SvnRevisionNumber)afterRevision.getRevisionNumber()).getRevision();
    }
    else {
      return SVNRevision.create(((SvnRevisionNumber)notNull(change.getBeforeRevision()).getRevisionNumber()).getRevision().getNumber() + 1);
    }
  }

  @NotNull
  private static String getDiffWindowTitle(@NotNull Change change) {
    if (change.isMoved() || change.isRenamed()) {
      final FilePath beforeFilePath = ChangesUtil.getBeforePath(change);
      final FilePath afterFilePath = ChangesUtil.getAfterPath(change);

      final String beforePath = beforeFilePath == null ? "" : beforeFilePath.getPath();
      final String afterPath = afterFilePath == null ? "" : afterFilePath.getPath();
      return SvnBundle.message("action.Subversion.properties.difference.diff.for.move.title", beforePath, afterPath);
    } else {
      return SvnBundle.message("action.Subversion.properties.difference.diff.title", ChangesUtil.getFilePath(change).getPath());
    }
  }

  private static int compareRevisions(@NotNull SVNRevision revision1, @NotNull SVNRevision revision2) {
    if (revision1.equals(revision2)) {
      return 0;
    }
    // working(local) ahead of head
    if (SVNRevision.WORKING.equals(revision1)) {
      return 1;
    }
    if (SVNRevision.WORKING.equals(revision2)) {
      return -1;
    }
    if (SVNRevision.HEAD.equals(revision1)) {
      return 1;
    }
    if (SVNRevision.HEAD.equals(revision2)) {
      return -1;
    }
    return revision1.getNumber() > revision2.getNumber() ? 1 : -1;
  }

  @NotNull
  private static String revisionToString(@Nullable SVNRevision revision) {
    return revision == null ? "not exists" : revision.toString();
  }

  private final static String ourPropertiesDelimiter = "\n";

  @NotNull
  private static List<PropertyData> getPropertyList(@NotNull SvnVcs vcs,
                                                    @Nullable final ContentRevision contentRevision,
                                                    @Nullable final SVNRevision revision)
  throws SVNException, VcsException {
    if (contentRevision == null) {
      return Collections.emptyList();
    }

    SvnTarget target;
    if (contentRevision instanceof SvnRepositoryContentRevision) {
      final SvnRepositoryContentRevision svnRevision = (SvnRepositoryContentRevision)contentRevision;
      target = SvnTarget.fromURL(SVNURL.parseURIEncoded(svnRevision.getFullPath()), revision);
    } else {
      final File ioFile = contentRevision.getFile().getIOFile();
      target = SvnTarget.fromFile(ioFile, revision);
    }

    return getPropertyList(vcs, target, revision);
  }

  @NotNull
  public static List<PropertyData> getPropertyList(@NotNull SvnVcs vcs, @NotNull final SVNURL url, @Nullable final SVNRevision revision)
    throws VcsException {
    return getPropertyList(vcs, SvnTarget.fromURL(url, revision), revision);
  }

  @NotNull
  public static List<PropertyData> getPropertyList(@NotNull SvnVcs vcs, @NotNull final File ioFile, @Nullable final SVNRevision revision)
    throws SVNException {
    try {
      return getPropertyList(vcs, SvnTarget.fromFile(ioFile, revision), revision);
    }
    catch (VcsException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, e), e);
    }
  }

  @NotNull
  private static List<PropertyData> getPropertyList(@NotNull SvnVcs vcs, @NotNull SvnTarget target, @Nullable SVNRevision revision)
    throws VcsException {
    final List<PropertyData> lines = new ArrayList<>();
    final PropertyConsumer propertyHandler = createHandler(revision, lines);

    vcs.getFactory(target).createPropertyClient().list(target, revision, Depth.EMPTY, propertyHandler);

    return lines;
  }

  @NotNull
  private static PropertyConsumer createHandler(SVNRevision revision, @NotNull final List<PropertyData> lines) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText(SvnBundle.message("show.properties.diff.progress.text.revision.information", revision.toString()));
    }

    return new PropertyConsumer() {
      public void handleProperty(final File path, final PropertyData property) {
        registerProperty(property);
      }

      public void handleProperty(final SVNURL url, final PropertyData property) {
        registerProperty(property);
      }

      public void handleProperty(final long revision, final PropertyData property) {
        // revision properties here
      }

      private void registerProperty(@NotNull PropertyData property) {
        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText2(SvnBundle.message("show.properties.diff.progress.text2.property.information", property.getName()));
        }
        lines.add(property);
      }
    };
  }

  @NotNull
  public static String toSortedStringPresentation(@NotNull List<PropertyData> lines) {
    StringBuilder sb = new StringBuilder();

    Collections.sort(lines, Comparator.comparing(PropertyData::getName));

    for (PropertyData line : lines) {
      addPropertyPresentation(line, sb);
    }

    return sb.toString();
  }

  private static void addPropertyPresentation(@NotNull PropertyData property, @NotNull StringBuilder sb) {
    if (sb.length() != 0) {
      sb.append(ourPropertiesDelimiter);
    }
    sb.append(property.getName()).append("=").append(StringUtil.notNullize(PropertyValue.toString(property.getValue())));
  }
}
