package com.intellij.openapi.vcs.history.impl;

import com.intellij.diff.Block;
import com.intellij.diff.FindBlock;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
 * author: lesya
 */
public class VcsBlockHistoryDialog extends VcsHistoryDialog{

  private final int mySelectionStart;
  private final int mySelectionEnd;

  private final Map<VcsFileRevision, Block> myRevisionToContentMap = new com.intellij.util.containers.HashMap<VcsFileRevision, Block>();

  public VcsBlockHistoryDialog(Project project, final VirtualFile file,
                               AbstractVcs vcs,
                               VcsHistoryProvider provider,
                               VcsHistorySession session, int selectionStart, int selectionEnd) {
    this(project, file, vcs, provider, session, selectionStart, selectionEnd, "History for Selection");
  }

  public VcsBlockHistoryDialog(Project project,
                               final VirtualFile file,
                               AbstractVcs vcs,
                               VcsHistoryProvider provider,
                               VcsHistorySession session,
                               int selectionStart,
                               int selectionEnd,
                               final String title) {
    super(project, file, provider, session,vcs);
    mySelectionStart = selectionStart;
    mySelectionEnd = selectionEnd;
    setTitle(title);
  }

  protected String getContentToShow(VcsFileRevision revision) {
    final Block block = getBlock(revision);
    if (block == null) return "";
    return block.getBlockContent();
  }

  @Nullable
  private Block getBlock(VcsFileRevision revision){
    if (myRevisionToContentMap.containsKey(revision))
      return myRevisionToContentMap.get(revision);

    int index = myRevisions.indexOf(revision);

    final String revisionContent = getContentOf(revision);
    if (revisionContent == null) return null;
    if (index == 0)
      myRevisionToContentMap.put(revision, new Block(revisionContent,  mySelectionStart, mySelectionEnd));
    else {
      Block prevBlock = getBlock(myRevisions.get(index - 1));
      if (prevBlock == null) return null;
      myRevisionToContentMap.put(revision, new FindBlock(revisionContent, prevBlock).getBlockInThePrevVersion());
    }
    return myRevisionToContentMap.get(revision);
  }

  protected VcsFileRevision[] revisionsNeededToBeLoaded(VcsFileRevision[] revisions) {
    Collection<VcsFileRevision> result = new HashSet<VcsFileRevision>();
    for (VcsFileRevision revision : revisions) {
      result.addAll(collectRevisionsFromFirstTo(revision));
    }

    return result.toArray(new VcsFileRevision[result.size()]);
  }

  private Collection<VcsFileRevision> collectRevisionsFromFirstTo(VcsFileRevision revision) {
    ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
    for (VcsFileRevision vcsFileRevision : myRevisions) {
      if (VcsHistoryUtil.compare(revision, vcsFileRevision) > 0) continue;
      result.add(vcsFileRevision);
    }
    return result;
  }
}
