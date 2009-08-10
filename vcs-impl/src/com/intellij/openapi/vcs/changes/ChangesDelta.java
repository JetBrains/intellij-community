package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.impl.CollectionsDelta;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ChangesDelta {
  private final PlusMinus<Pair<String, AbstractVcs>> myDeltaListener;
  private ProjectLevelVcsManager myVcsManager;
  private boolean myInitialized;

  public ChangesDelta(final Project project, final PlusMinus<Pair<String, AbstractVcs>> deltaListener) {
    myDeltaListener = deltaListener;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  public void step(final ChangeListsIndexes was, final ChangeListsIndexes became) {
    List<Pair<String, VcsKey>> wasAffected = was.getAffectedFilesUnderVcs();
    if (! myInitialized) {
      wasAffected = fillVcsKeys(wasAffected);
      sendPlus(wasAffected);
      myInitialized = true;
      return;
    }
    final List<Pair<String, VcsKey>> becameAffected = became.getAffectedFilesUnderVcs();

    final Set<Pair<String,VcsKey>> toRemove = CollectionsDelta.notInSecond(wasAffected, becameAffected);
    final Set<Pair<String, VcsKey>> toAdd = CollectionsDelta.notInSecond(becameAffected, wasAffected);

    if (toRemove != null) {
      for (Pair<String, VcsKey> pair : toRemove) {
        myDeltaListener.minus(convertPair(pair));
      }
    }
    sendPlus(toAdd);
  }

  private void sendPlus(final Collection<Pair<String, VcsKey>> toAdd) {
    if (toAdd != null) {
      for (Pair<String, VcsKey> pair : toAdd) {
        myDeltaListener.plus(convertPair(pair));
      }
    }
  }

  private Pair<String, AbstractVcs> convertPair(final Pair<String, VcsKey> pair) {
    return new Pair<String, AbstractVcs>(pair.getFirst(), myVcsManager.findVcsByName(pair.getSecond().getName()));
  }

  // we expect only "was" to don't know what vcses are - after deserialization
  private List<Pair<String, VcsKey>> fillVcsKeys(final List<Pair<String, VcsKey>> coll) {
    List<Pair<String, VcsKey>> converted = null;
    for (int i = 0; i < coll.size(); i++) {
      final Pair<String, VcsKey> pair = coll.get(i);
      if (pair.getSecond() == null) {
        final String fileKey = pair.getFirst();
        final VcsKey vcsKey = findVcs(fileKey);
        if (converted == null) {
          converted = new ArrayList<Pair<String, VcsKey>>(coll.size());
          // copy first i-1
          for (int j = 0; j < i; j++) {
            converted.add(coll.get(j));
          }
        }

        converted.add(new Pair<String, VcsKey>(fileKey, vcsKey));
      }
    }
    return converted != null ? converted : coll;
  }

  @Nullable
  private VcsKey findVcs(final String path) {
    // does not matter directory or not
    final AbstractVcs vcs = myVcsManager.getVcsFor(FilePathImpl.create(new File(path), false));
    return vcs == null ? null : vcs.getKeyInstanceMethod();
  }
}
