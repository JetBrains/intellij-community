package com.intellij.openapi.vcs.history.impl;

import com.intellij.diff.Block;
import com.intellij.diff.FindBlock;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

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
                               VcsHistorySession session, int selectionStart, int selectionEnd){
    super(project, file, provider, session,vcs);
    mySelectionStart = selectionStart;
    mySelectionEnd = selectionEnd;
  }

  protected String getContentToShow(VcsFileRevision revision) {
    return getBlock(revision).getBlockContent();
  }

  private Block getBlock(VcsFileRevision revision){
    if (myRevisionToContentMap.containsKey(revision))
      return myRevisionToContentMap.get(revision);

    int index = myRevisions.indexOf(revision);

    if (index == 0)
      myRevisionToContentMap.put(revision, new Block(getContentOf(revision),  mySelectionStart, mySelectionEnd));
    else {
      Block prevBlock = getBlock(myRevisions.get(index - 1));
      myRevisionToContentMap.put(revision, new FindBlock(getContentOf(revision), prevBlock).getBlockInThePrevVersion());
    }
    return myRevisionToContentMap.get(revision);
  }

  protected VcsFileRevision[] revisionsNeededToBeLoaded(VcsFileRevision[] revisions) {
    Collection<VcsFileRevision> result = new HashSet<VcsFileRevision>();
    for (int i = 0; i < revisions.length; i++) {
      VcsFileRevision revision = revisions[i];
      result.addAll(collectRevisionsFromFirstTo(revision));
    }

    return result.toArray(new VcsFileRevision[result.size()]);
  }

  private Collection<VcsFileRevision> collectRevisionsFromFirstTo(VcsFileRevision revision) {
    ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
    for (Iterator iterator = myRevisions.iterator(); iterator.hasNext();) {
      VcsFileRevision vcsFileRevision = (VcsFileRevision) iterator.next();
      if (VcsHistoryUtil.compare(revision, vcsFileRevision) > 0) continue;
      result.add(vcsFileRevision);
    }
    return result;
  }
}
