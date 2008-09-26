package com.intellij.openapi.vcs.changes.local;

import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListWorker;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.EventDispatcher;

public class EditComment implements ChangeListCommand {
  private final String myName;
  private final String myNewComment;
  private String myOldComment;
  private LocalChangeList myListCopy;

  public EditComment(final String name, final String newComment) {
    myNewComment = newComment;
    myName = name;
  }

  public void apply(final ChangeListWorker worker) {
    myOldComment = worker.editComment(myName, myNewComment);
    myListCopy = worker.getCopyByName(myName);
  }

  public void doNotify(final EventDispatcher<ChangeListListener> dispatcher) {
    dispatcher.getMulticaster().changeListCommentChanged(myListCopy, myOldComment);
  }

  public String getOldComment() {
    return myOldComment;
  }
}
