package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.CompareWithSelectedRevisionAction;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;

import java.util.Arrays;
import java.util.List;

public class HighlightAnnotationsActions {
  private final HightlightAction myBefore;
  private final HightlightAction myAfter;
  private final RemoveHighlightingAction myRemove;

  public HighlightAnnotationsActions(final Project project, final VirtualFile virtualFile, final FileAnnotation fileAnnotation) {
    myBefore = new HightlightAction(true, project, virtualFile, fileAnnotation);
    myAfter = new HightlightAction(false, project, virtualFile, fileAnnotation);
    myRemove = new RemoveHighlightingAction();
  }

  public List<AnAction> getList() {
    return Arrays.asList(myBefore, myAfter, myRemove);
  }

  public boolean isLineBold(final int lineNumber) {
    if (turnedOn()) {
      if (myBefore.isTurnedOn() && (! myBefore.isBold(lineNumber))) {
        return false;
      }
      if (myAfter.isTurnedOn() && (! myAfter.isBold(lineNumber))) {
        return false;
      }
      return true;
    }
    return false;
  }

  private boolean turnedOn() {
    return myBefore.isTurnedOn() || myAfter.isTurnedOn();
  }

  private class RemoveHighlightingAction extends AnAction {
    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setText("Remove highlighting");
      e.getPresentation().setEnabled(turnedOn());
    }

    public void actionPerformed(final AnActionEvent e) {
      myBefore.clear();
      myAfter.clear();
    }
  }

  private static class HightlightAction extends AnAction {
    private final Project myProject;
    private final VirtualFile myVirtualFile;
    private final FileAnnotation myFileAnnotation;
    private final boolean myBefore;
    private VcsFileRevision mySelectedRevision;
    private Boolean myShowComments;

    private HightlightAction(final boolean before, final Project project, final VirtualFile virtualFile, final FileAnnotation fileAnnotation) {
      myBefore = before;
      myProject = project;
      myVirtualFile = virtualFile;
      myFileAnnotation = fileAnnotation;
      myShowComments = null;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final String text;
      final String description;
      if (myBefore) {
        text = (mySelectedRevision == null) ? VcsBundle.message("highlight.annotation.before.not.selected.text") :
               VcsBundle.message("highlight.annotation.before.selected.text", mySelectedRevision.getRevisionNumber().asString());
        description = VcsBundle.message("highlight.annotation.before.description");
      } else {
        text = (mySelectedRevision == null) ? VcsBundle.message("highlight.annotation.after.not.selected.text") :
               VcsBundle.message("highlight.annotation.after.selected.text", mySelectedRevision.getRevisionNumber().asString());
        description = VcsBundle.message("highlight.annotation.after.description");
      }
      e.getPresentation().setText(text);
      e.getPresentation().setDescription(description);
      final List<VcsFileRevision> fileRevisionList = myFileAnnotation.getRevisions();
      e.getPresentation().setEnabled(fileRevisionList != null && (! fileRevisionList.isEmpty()));
    }

    public void actionPerformed(final AnActionEvent e) {
      final List<VcsFileRevision> fileRevisionList = myFileAnnotation.getRevisions();
      if (fileRevisionList != null) {
        if (myShowComments == null) {
          initShowComments(fileRevisionList);
        }
        CompareWithSelectedRevisionAction.showListPopup(fileRevisionList, myProject,
                                                        new Consumer<VcsFileRevision>() {
                                                          public void consume(final VcsFileRevision vcsFileRevision) {
                                                            mySelectedRevision = vcsFileRevision;
                                                          }
                                                        }, myShowComments.booleanValue());
      }
    }

    private void initShowComments(final List<VcsFileRevision> revisions) {
      for (VcsFileRevision revision : revisions) {
        if (revision.getCommitMessage() != null) {
          myShowComments = true;
          return;
        }
      }
      myShowComments = false;
    }

    public boolean isTurnedOn() {
      return mySelectedRevision != null;
    }

    public void clear() {
      mySelectedRevision = null;
    }

    public boolean isBold(final int line) {
      if (mySelectedRevision != null) {
        final VcsRevisionNumber number = myFileAnnotation.getLineRevisionNumber(line);
        if (number != null) {
          final int compareResult = number.compareTo(mySelectedRevision.getRevisionNumber());
          return (myBefore && compareResult <= 0) || ((! myBefore) && (compareResult >= 0));
        }
      }
      return false;
    }
  }
}
