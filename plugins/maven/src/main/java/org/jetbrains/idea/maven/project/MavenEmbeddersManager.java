package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MavenEmbeddersManager {
  private final MavenGeneralSettings myGeneralSettings;

  private final LinkedList<MavenEmbedderWrapper> myPool = new LinkedList<MavenEmbedderWrapper>();

  private final List<MavenEmbedderWrapper> myEmbeddersInUse = new ArrayList<MavenEmbedderWrapper>();
  private final List<MavenEmbedderWrapper> myEmbeddersToReset = new ArrayList<MavenEmbedderWrapper>();

  public MavenEmbeddersManager(MavenGeneralSettings generalSettings) {
    myGeneralSettings = generalSettings;
  }

  public synchronized void reset() {
    releasePooledEmbedders();
    myEmbeddersToReset.addAll(myEmbeddersInUse);
  }

  public synchronized MavenEmbedderWrapper getEmbedder() {
    MavenEmbedderWrapper result = myPool.poll();
    if (result == null) {
      result = MavenEmbedderFactory.createEmbedder(myGeneralSettings);
    }
    myEmbeddersInUse.add(result);
    return result;
  }

  public synchronized void release(MavenEmbedderWrapper embedder) {
    assert myEmbeddersInUse.contains(embedder);

    myEmbeddersInUse.remove(embedder);
    boolean isObsolete = myEmbeddersToReset.remove(embedder);
    if (isObsolete || myPool.size() > 3) {
      embedder.release();
    }
    else {
      embedder.reset();
      myPool.add(embedder);
    }
  }

  public synchronized void release() {
    assert myEmbeddersInUse.isEmpty();
    releasePooledEmbedders();
  }

  private synchronized void releasePooledEmbedders() {
    for (MavenEmbedderWrapper each : myPool) {
      each.release();
    }
    myPool.clear();
  }
}
