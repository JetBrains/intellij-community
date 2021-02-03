// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import gnu.trove.TIntObjectHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.source.ArchetypeDataSource;
import org.apache.maven.archetype.source.ArchetypeDataSourceException;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.wagon.events.TransferEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.server.security.MavenToken;
import org.sonatype.nexus.index.*;
import org.sonatype.nexus.index.context.IndexUtils;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.creator.JarFileContentsIndexCreator;
import org.sonatype.nexus.index.creator.MinimalArtifactInfoIndexCreator;
import org.sonatype.nexus.index.updater.IndexUpdateRequest;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.*;

public abstract class Maven3ServerIndexerImpl extends MavenRemoteObject implements MavenServerIndexer {

  private final Maven3ServerEmbedder myEmbedder;
  private final NexusIndexer myIndexer;
  private final IndexUpdater myUpdater;
  private final ArtifactContextProducer myArtifactContextProducer;

  private final TIntObjectHashMap<IndexingContext> myIndices = new TIntObjectHashMap<IndexingContext>();

  public Maven3ServerIndexerImpl(Maven3ServerEmbedder embedder) {
    myEmbedder = embedder;

    myIndexer = myEmbedder.getComponent(NexusIndexer.class);
    myUpdater = myEmbedder.getComponent(IndexUpdater.class);
    myArtifactContextProducer = myEmbedder.getComponent(ArtifactContextProducer.class);

    MavenServerUtil.registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        release(MavenServerUtil.getToken());
      }
    });
  }

  @Override
  public int createIndex(@NotNull String indexId,
                         @NotNull String repositoryId,
                         @Nullable File file,
                         @Nullable String url,
                         @NotNull File indexDir, MavenToken token) throws MavenServerIndexerException {
    MavenServerUtil.checkToken(token);
    try {

      IndexingContext context = myIndexer.addIndexingContextForced(indexId,
                                                                   repositoryId,
                                                                   file,
                                                                   indexDir,
                                                                   url,
                                                                   null, // repo update url
                                                                   Arrays.asList(new TinyArtifactInfoIndexCreator(),
                                                                                 new JarFileContentsIndexCreator()));
      int id = System.identityHashCode(context);
      myIndices.put(id, context);
      return id;
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  @Override
  public void releaseIndex(int id, MavenToken token) throws MavenServerIndexerException {
    MavenServerUtil.checkToken(token);
    try {
      myIndexer.removeIndexingContext(getIndex(id), false);
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  @NotNull
  private IndexingContext getIndex(int id) {
    IndexingContext index = myIndices.get(id);
    if (index == null) throw new RuntimeException("Index not found for id: " + id);
    return index;
  }

  @Override
  public boolean indexExists(File dir, MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      return IndexReader.indexExists(dir);
    }
    catch (Exception e) {
      Maven3ServerGlobals.getLogger().warn(e);
    }
    return false;
  }

  @Override
  public int getIndexCount(MavenToken token) {
    MavenServerUtil.checkToken(token);
    return myIndexer.getIndexingContexts().size();
  }

  private static String getRepositoryPathOrUrl(IndexingContext index) {
    File file = index.getRepository();
    return file == null ? index.getRepositoryUrl() : file.getPath();
  }

  @Override
  public void updateIndex(int id, MavenServerSettings settings, final MavenServerProgressIndicator indicator, MavenToken token)
    throws RemoteException, MavenServerIndexerException, MavenServerProcessCanceledException {
    MavenServerUtil.checkToken(token);
    final IndexingContext index = getIndex(id);

    try {
      File repository = index.getRepository();
      if (repository != null) { // is local repository
        if (repository.exists()) {
          indicator.setIndeterminate(true);
          try {
            myIndexer.scan(index, new MyScanningListener(indicator), false);
          }
          finally {
            indicator.setIndeterminate(false);
          }
        }
      }
      else {
        final Maven3ServerEmbedder embedder = createEmbedder(settings);

        MavenExecutionRequest r =
          embedder.createRequest(null, null, null, null);

        final IndexUpdateRequest request = new IndexUpdateRequest(index);

        try {
          embedder.executeWithMavenSession(r, new Runnable() {
            @Override
            public void run() {
              request.setResourceFetcher(new Maven3ServerIndexFetcher(index.getRepositoryId(),
                                                                      index.getRepositoryUrl(),
                                                                      embedder.getComponent(WagonManager.class),
                                                                      embedder.getComponent(RepositorySystem.class),
                                                                      new WagonTransferListenerAdapter(indicator) {
                                                                        @Override
                                                                        protected void downloadProgress(long downloaded, long total) {
                                                                          super.downloadProgress(downloaded, total);
                                                                          try {
                                                                            myIndicator.setFraction(((double)downloaded) / total);
                                                                          }
                                                                          catch (RemoteException e) {
                                                                            throw new RuntimeRemoteException(e);
                                                                          }
                                                                        }

                                                                        @Override
                                                                        public void transferCompleted(TransferEvent event) {
                                                                          super.transferCompleted(event);
                                                                          try {
                                                                            myIndicator.setText2("Processing indices...");
                                                                          }
                                                                          catch (RemoteException e) {
                                                                            throw new RuntimeRemoteException(e);
                                                                          }
                                                                        }
                                                                      }));
              try {
                myUpdater.fetchAndUpdateIndex(request);
              }
              catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          });
        }
        finally {
          embedder.release(token);
        }
      }
    }
    catch (RuntimeRemoteException e) {
      throw e.getCause();
    }
    catch (MavenProcessCanceledRuntimeException e) {
      throw new MavenServerProcessCanceledException();
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  public abstract Maven3ServerEmbedder createEmbedder(MavenServerSettings settings) throws RemoteException;


  @Override
  public void processArtifacts(int indexId, MavenServerIndicesProcessor processor, MavenToken token)
    throws MavenServerIndexerException {
    MavenServerUtil.checkToken(token);
    try {
      final int CHUNK_SIZE = 10000;

      IndexReader r = getIndex(indexId).getIndexReader();
      int total = r.numDocs();

      List<IndexedMavenId> result = new ArrayList<IndexedMavenId>(Math.min(CHUNK_SIZE, total));
      for (int i = 0; i < total; i++) {
        if (r.isDeleted(i)) continue;

        Document doc = r.document(i);
        String uinfo = doc.get(ArtifactInfo.UINFO);
        if (uinfo == null) continue;
        String[] uInfoParts = uinfo.split("\\|");
        if (uInfoParts.length < 3) continue;
        String groupId = uInfoParts[0];
        String artifactId = uInfoParts[1];
        String version = uInfoParts[2];

        String packaging = doc.get(ArtifactInfo.PACKAGING);
        String description = doc.get(ArtifactInfo.DESCRIPTION);

        result.add(new IndexedMavenId(groupId, artifactId, version, packaging, description));

        if (result.size() == CHUNK_SIZE) {
          processor.processArtifacts(result);
          result.clear();
        }
      }

      if (!result.isEmpty()) {
        processor.processArtifacts(result);
      }
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  @Override
  public IndexedMavenId addArtifact(int indexId, File artifactFile, MavenToken token) throws MavenServerIndexerException {
    MavenServerUtil.checkToken(token);
    try {
      IndexingContext index = getIndex(indexId);
      ArtifactContext artifactContext = myArtifactContextProducer.getArtifactContext(index, artifactFile);
      if (artifactContext == null) return null;

      addArtifact(myIndexer, index, artifactContext);

      ArtifactInfo a = artifactContext.getArtifactInfo();
      return new IndexedMavenId(a.groupId, a.artifactId, a.version, a.packaging, a.description);
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  public static void addArtifact(NexusIndexer indexer, IndexingContext index, ArtifactContext artifactContext)
    throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    indexer.addArtifactToIndex(artifactContext, index);
    // this hack is necessary to invalidate searcher's and reader's cache (may not be required then lucene or nexus library change
    Method m = index.getClass().getDeclaredMethod("closeReaders");
    m.setAccessible(true);
    m.invoke(index);
  }


  @Override
  public Set<MavenArtifactInfo> search(int indexId, Query query, int maxResult, MavenToken token)
    throws MavenServerIndexerException {
    MavenServerUtil.checkToken(token);
    try {
      IndexingContext index = getIndex(indexId);

      TopDocs docs = null;
      try {
        BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
        docs = index.getIndexSearcher().search(query, null, maxResult);
      }
      catch (BooleanQuery.TooManyClauses ignore) {
        // this exception occurs when too wide wildcard is used on too big data.
      }

      if (docs == null || docs.scoreDocs.length == 0) return Collections.emptySet();

      Set<MavenArtifactInfo> result = new HashSet<MavenArtifactInfo>();

      for (int i = 0; i < docs.scoreDocs.length; i++) {
        int docIndex = docs.scoreDocs[i].doc;
        Document doc = index.getIndexReader().document(docIndex);
        ArtifactInfo a = IndexUtils.constructArtifactInfo(doc, index);
        if (a == null) continue;

        a.repository = getRepositoryPathOrUrl(index);
        result.add(MavenModelConverter.convertArtifactInfo(a));
      }
      return result;
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  @Override
  public Collection<MavenArchetype> getArchetypes(MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    Set<MavenArchetype> result = new HashSet<MavenArchetype>();
    doCollectArchetypes("internal-catalog", result);
    return result;
  }

  private void doCollectArchetypes(String roleHint, Set<MavenArchetype> result) throws RemoteException {
    try {
      ArchetypeDataSource source = myEmbedder.getComponent(ArchetypeDataSource.class, roleHint);
      ArchetypeCatalog archetypeCatalog = source.getArchetypeCatalog(new Properties());

      for (Archetype each : archetypeCatalog.getArchetypes()) {
        result.add(MavenModelConverter.convertArchetype(each));
      }
    }
    catch (ArchetypeDataSourceException e) {
      Maven3ServerGlobals.getLogger().warn(e);
    }
  }

  @Override
  public void release(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      myEmbedder.release(token);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  private static class MyScanningListener implements ArtifactScanningListener {
    private final MavenServerProgressIndicator p;

    MyScanningListener(MavenServerProgressIndicator indicator) {
      p = indicator;
    }

    @Override
    public void scanningStarted(IndexingContext ctx) {
      try {
        if (p.isCanceled()) throw new MavenProcessCanceledRuntimeException();
      }
      catch (RemoteException e) {
        throw new RuntimeRemoteException(e);
      }
    }

    @Override
    public void scanningFinished(IndexingContext ctx, ScanningResult result) {
      try {
        if (p.isCanceled()) throw new MavenProcessCanceledRuntimeException();
      }
      catch (RemoteException e) {
        throw new RuntimeRemoteException(e);
      }
    }

    @Override
    public void artifactError(ArtifactContext ac, Exception e) {
    }

    @Override
    public void artifactDiscovered(ArtifactContext ac) {
      try {
        if (p.isCanceled()) throw new MavenProcessCanceledRuntimeException();
        ArtifactInfo info = ac.getArtifactInfo();
        p.setText2(info.groupId + ":" + info.artifactId + ":" + info.version);
      }
      catch (RemoteException e) {
        throw new RuntimeRemoteException(e);
      }
    }
  }

  private static class TinyArtifactInfoIndexCreator extends MinimalArtifactInfoIndexCreator {

    private static final IndexerField FLD_PACKAGING_NOT_INDEXED =
      new IndexerField(MAVEN.PACKAGING, IndexerFieldVersion.V1, ArtifactInfo.PACKAGING,"Artifact Packaging (not indexed, stored)",
                       Field.Store.YES, Field.Index.NO);

    private static final IndexerField FLD_DESCRIPTION_NOT_INDEXED =
      new IndexerField(MAVEN.DESCRIPTION, IndexerFieldVersion.V1, ArtifactInfo.DESCRIPTION, "Artifact description (not indexed, stored)",
                       Field.Store.YES, Field.Index.NO);

    @Override
    public void updateDocument(ArtifactInfo ai, Document doc) {
      if (ai.packaging != null) {
        doc.add(FLD_PACKAGING_NOT_INDEXED.toField(ai.packaging));
      }

      if ("maven-archetype".equals(ai.packaging) && ai.description != null) {
        doc.add(FLD_DESCRIPTION_NOT_INDEXED.toField(ai.description));
      }
    }

    @Override
    public Collection<IndexerField> getIndexerFields() {
      return Arrays.asList(FLD_PACKAGING_NOT_INDEXED, FLD_DESCRIPTION_NOT_INDEXED);
    }
  }
}
