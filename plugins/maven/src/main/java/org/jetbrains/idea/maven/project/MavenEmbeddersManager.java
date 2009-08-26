package org.jetbrains.idea.maven.project;

import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SoftValueHashMap;
import gnu.trove.THashMap;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MavenEmbeddersManager {
  private final MavenGeneralSettings myGeneralSettings;

  private final Map<Object, LinkedList<MavenEmbedderWrapper>> myPools = new SoftValueHashMap<Object, LinkedList<MavenEmbedderWrapper>>();

  private final MultiMap<Object, MavenEmbedderWrapper> myEmbeddersInUse = new MultiMap<Object, MavenEmbedderWrapper>();
  private final List<MavenEmbedderWrapper> myEmbeddersToReset = new ArrayList<MavenEmbedderWrapper>();

  private volatile Map myContext;

  public MavenEmbeddersManager(MavenGeneralSettings generalSettings) {
    myGeneralSettings = generalSettings;
    resetContext();
  }

  public synchronized void reset() {
    releasePooledEmbedders();
    myEmbeddersToReset.addAll(myEmbeddersInUse.values());
    resetContext();
  }

  public synchronized void clearCaches() {
    // todo todo
    //((MavenSharedCache)myContext.get(CustomWorkspaceStore.SHARED_CACHE)).clear();
    for (MavenEmbedderWrapper each : myEmbeddersInUse.values()) {
      each.clearCaches();
    }
    for (Object eachKey : myPools.keySet()) {
      LinkedList<MavenEmbedderWrapper> eachPool = myPools.get(eachKey);
      if (eachPool == null) continue; // collected
      for (MavenEmbedderWrapper each : eachPool) {
        each.clearCaches();
      }
    }
  }

  public synchronized void clearCachesFor(MavenProject project) {
    // todo todo
    //((MavenSharedCache)myContext.get(CustomWorkspaceStore.SHARED_CACHE)).clearCachesFor(project);
  }

  private synchronized void resetContext() {
    myContext = new THashMap();
    // todo todo
    //myContext.put(CustomWorkspaceStore.SHARED_CACHE, new MavenSharedCache());
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

    MavenEmbedderWrapper result = MavenEmbedderFactory.createEmbedder(myGeneralSettings, myContext);
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
    for (Object eachKey : myPools.keySet()) {
      LinkedList<MavenEmbedderWrapper> eachPool = myPools.get(eachKey);
      if (eachPool == null) continue; // collected
      for (MavenEmbedderWrapper each : eachPool) {
        each.release();
      }
      eachPool.clear();
    }
    myPools.clear();
  }
}
