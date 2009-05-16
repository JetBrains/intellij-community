package org.jetbrains.idea.maven.project;

import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SoftHashMap;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MavenEmbeddersManager {
  private final MavenGeneralSettings myGeneralSettings;

  private final Map<Object, LinkedList<MavenEmbedderWrapper>> myPools = new SoftHashMap<Object, LinkedList<MavenEmbedderWrapper>>();

  private final MultiMap<Object, MavenEmbedderWrapper> myEmbeddersInUse = new MultiMap<Object, MavenEmbedderWrapper>();
  private final List<MavenEmbedderWrapper> myEmbeddersToReset = new ArrayList<MavenEmbedderWrapper>();

  public MavenEmbeddersManager(MavenGeneralSettings generalSettings) {
    myGeneralSettings = generalSettings;
  }

  public synchronized void reset() {
    releasePooledEmbedders();
    myEmbeddersToReset.addAll(myEmbeddersInUse.values());
  }

  public MavenEmbedderWrapper getEmbedder(Object id) {
    synchronized (this) {
      LinkedList<MavenEmbedderWrapper> pool;
      pool = myPools.get(id);
      if (pool != null) {
        MavenEmbedderWrapper result = pool.poll();
        if (result != null) {
          myEmbeddersInUse.putValue(id, result);
          return result;
        }
      }
    }

    MavenEmbedderWrapper result = MavenEmbedderFactory.createEmbedder(myGeneralSettings);
    synchronized (this) {
      myEmbeddersInUse.putValue(id, result);
    }
    return result;
  }

  public synchronized void release(MavenEmbedderWrapper embedder) {
    Object id = null;
    for (Object eachId : myEmbeddersInUse.keySet()) {
      if (myEmbeddersInUse.get(eachId).contains(embedder)) {
        id = eachId;
        break;
      }
    }
    assert id != null : "embedder not found";
    myEmbeddersInUse.removeValue(id, embedder);

    LinkedList<MavenEmbedderWrapper> pool = myPools.get(id);

    boolean isObsolete = myEmbeddersToReset.remove(embedder);
    if (isObsolete || (pool != null && pool.size() > 1)) {
      embedder.release();
      return;
    }

    if (pool == null) {
      pool = new LinkedList<MavenEmbedderWrapper>();
      myPools.put(id, pool);
    }
    embedder.reset();
    pool.add(embedder);
  }

  public synchronized void release() {
    assert myEmbeddersInUse.isEmpty();
    releasePooledEmbedders();
  }

  public synchronized void releaseForceefullyInTests() {
    releasePooledEmbedders();
    for (MavenEmbedderWrapper each : myEmbeddersInUse.values()) {
      each.release();
    }
  }

  private synchronized void releasePooledEmbedders() {
    for (LinkedList<MavenEmbedderWrapper> eachPool : myPools.values()) {
      for (MavenEmbedderWrapper each : eachPool) {
        each.release();
      }
      eachPool.clear();
    }
    myPools.clear();
  }
}
