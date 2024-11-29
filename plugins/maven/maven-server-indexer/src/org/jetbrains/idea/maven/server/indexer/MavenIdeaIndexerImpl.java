// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.indexer;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.maven.archetype.ArchetypeManager;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.index.Scanner;
import org.apache.maven.index.*;
import org.apache.maven.index.context.DefaultIndexingContext;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.creator.JarFileContentsIndexCreator;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.apache.maven.index.updater.*;
import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenIndexId;
import org.jetbrains.idea.maven.server.*;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenIdeaIndexerImpl extends MavenRemoteObject implements MavenServerIndexer {

  protected final Map<String, IndexingContext> myContexts = new HashMap<>();
  protected final Set<String> myLocks = Collections.synchronizedSet(new HashSet<>());
  private final Indexer myIndexer;
  private final IndexUpdater myUpdater;
  private final Scanner myScanner;
  private final PlexusContainer myContainer;
  private final ArtifactContextProducer myArtifactContextProducer;
  private final ExecutorService myRemoteExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "indexing-remote"));

  public MavenIdeaIndexerImpl(PlexusContainer container) throws ComponentLookupException {
    myContainer = container;
    myIndexer = myContainer.lookup(Indexer.class);
    myUpdater = myContainer.lookup(IndexUpdater.class);
    myScanner = myContainer.lookup(Scanner.class);
    myArtifactContextProducer = myContainer.lookup(ArtifactContextProducer.class);


    MavenServerUtil.registerShutdownTask(() -> release(MavenServerUtil.getToken()));
  }

  @Override
  public void releaseIndex(MavenIndexId mavenIndexId, MavenToken token) throws MavenServerIndexerException {
    MavenServerUtil.checkToken(token);
    try {
      synchronized (myContexts) {
        IndexingContext context = myContexts.remove(mavenIndexId.indexId);
        if (context != null) context.close(false);
      }
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  private List<IndexCreator> createIndexers() throws ComponentLookupException {
    List<IndexCreator> result = new ArrayList<>();
    result.add(myContainer.lookup(IndexCreator.class, MinimalArtifactInfoIndexCreator.ID));
    result.add(myContainer.lookup(IndexCreator.class, JarFileContentsIndexCreator.ID));
    result.add(new TinyArtifactInfoIndexCreator());
    return result;
  }

  @NotNull
  private IndexingContext getIndex(MavenIndexId mavenIndexId) throws IOException, ComponentLookupException {
    synchronized (myContexts) {
      IndexingContext context = myContexts.get(mavenIndexId.indexId);
      if (context != null) return context;
      @NotNull String id = mavenIndexId.indexId;
      @NotNull String repositoryId = mavenIndexId.repositoryId;
      @Nullable File repository = mavenIndexId.repositoryFilePath == null ? null : new File(mavenIndexId.repositoryFilePath);
      @NotNull File indexDirectory = new File(mavenIndexId.indexDirPath);
      @Nullable String repositoryUrl = mavenIndexId.url;
      @Nullable String indexUpdateUrl = null;
      boolean searchable = true;
      boolean reclaim = true;
      context =
        myIndexer.createIndexingContext(id, repositoryId, repository, indexDirectory, repositoryUrl, indexUpdateUrl, searchable, reclaim,
                                        createIndexers());
      myContexts.put(mavenIndexId.indexId, context);
      return context;
    }
  }

  @Override
  public boolean indexExists(Path dir, MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      try (FSDirectory directory = FSDirectory.open(dir)) {
        return DirectoryReader.indexExists(directory);
      }
    }
    catch (Exception ignore) {
      return false;
    }
  }


  @Override
  public int getIndexCount(MavenToken token) {
    MavenServerUtil.checkToken(token);
    synchronized (myContexts) {
      return myContexts.size();
    }
  }

  @Override
  public void updateIndex(MavenIndexId mavenIndexId,
                          final MavenServerProgressIndicator remoteIndicator,
                          boolean multithreaded,
                          MavenToken token)
    throws RemoteException, MavenServerIndexerException, MavenServerProcessCanceledException {
    MavenServerUtil.checkToken(token);
    MavenServerProgressIndicator indicator = new MavenServerSideCancelledThrottler(remoteIndicator);
    doUpdateIndex(mavenIndexId, multithreaded, indicator);
  }

  protected void doUpdateIndex(MavenIndexId mavenIndexId, boolean multithreaded, MavenServerProgressIndicator indicator)
    throws MavenServerProcessCanceledException, MavenServerIndexerException {
    try {
      final IndexingContext context = getIndex(mavenIndexId);
      boolean readyToUpdate = myLocks.add(mavenIndexId.indexId);

      if (readyToUpdate) {
        try {
          File repository = context.getRepository();
          if (repository != null) { // is local repository
            scanAndUpdateLocalRepositoryIndex(indicator, context, multithreaded);
          }
          else {
            downloadRemoteIndex(indicator, context);
          }
        }
        finally {
          myLocks.remove(mavenIndexId.indexId);
        }
      }
    }
    catch (
      MavenProcessCanceledRuntimeException e) {
      throw new MavenServerProcessCanceledException();
    }
    catch (Throwable e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  protected void downloadRemoteIndex(MavenServerProgressIndicator indicator, IndexingContext context)
    throws Throwable {

    Future<?> future = myRemoteExecutorService.submit(() -> {
      try {
        Wagon httpWagon = myContainer.lookup(Wagon.class, "http");
        ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher(httpWagon, new WagonTransferListenerAdapter(indicator),
                                                                       null, null);
        Date currentTimestamp = context.getTimestamp();
        IndexUpdateRequest request = new IndexUpdateRequest(context, resourceFetcher);
        indicator.setText("Updating index for " + context.getRepositoryUrl());
        IndexUpdateResult updateResult = myUpdater.fetchAndUpdateIndex(request);
        updateIndicatorStatus(indicator, context, updateResult, currentTimestamp);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    while (!indicator.isCanceled()) {
      try {
        future.get(100, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutException ignore) {
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Maven indexing was terminated");
      }
      catch (ExecutionException e) {
        throw e.getCause();
      }
    }
  }

  protected void scanAndUpdateLocalRepositoryIndex(MavenServerProgressIndicator indicator, IndexingContext context, boolean multithreaded)
    throws
    IOException {

    File repositoryDirectory = context.getRepository();
    if (repositoryDirectory == null || !repositoryDirectory.exists()) {
      throw new IOException("Repository directory " + repositoryDirectory + " does not exist");
    }
    indicator.setText("Scanning " + repositoryDirectory.getPath());

    File tmpDir = Files.createTempDirectory(context.getId() + "-tmp").toFile();

    IndexingContext tmpContext = null;

    try {

      tmpContext = new DefaultIndexingContext(context.getId() + "-tmp",
                                              context.getRepositoryId(),
                                              context.getRepository(),
                                              tmpDir,
                                              context.getRepositoryUrl(),
                                              context.getIndexUpdateUrl(),
                                              context.getIndexCreators(),
                                              true);

      myScanner.scan(new ScanningRequest(tmpContext,
                                         new DefaultScannerListener(tmpContext,
                                                                    myContainer.lookup(IndexerEngine.class),
                                                                    false,
                                                                    new MyScanningListener(indicator)),
                                         null));
      indicator.setText("Scanning for " + repositoryDirectory.getPath() + " complete, updating indices");
      tmpContext.updateTimestamp(true);
      context.replace(tmpContext.getIndexDirectory());
      indicator.setText("Indices for " + repositoryDirectory.getPath() + " updated");
    }
    catch (Exception e) {
      throw new IOException("Error scanning context " + context.getId(), e);
    }
    finally {
      if (tmpContext != null) {
        tmpContext.close(true);
      }
      FileUtils.deleteDirectory(tmpDir);
    }
  }

  @Override
  public ArrayList<IndexedMavenId> processArtifacts(MavenIndexId indexId, int startFrom, MavenToken token)
    throws MavenServerIndexerException {
    MavenServerUtil.checkToken(token);
    try {
      final int CHUNK_SIZE = 2000;

      IndexingContext context = getIndex(indexId);
      synchronized (context) {
        IndexSearcher searcher = context.acquireIndexSearcher();
        IndexReader r = searcher.getIndexReader();
        int total = r.numDocs();

        ArrayList<IndexedMavenId> result = new ArrayList<>(Math.min(CHUNK_SIZE, total));
        for (int i = startFrom; i < total; i++) {

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
            return result;
          }
        }

        if (result.isEmpty()) {
          return null;
        }
        else {
          return result;
        }
      }
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  @Override
  public @NotNull ArrayList<AddArtifactResponse> addArtifacts(@NotNull MavenIndexId indexId,
                                                              @NotNull ArrayList<Path> artifactFiles,
                                                              MavenToken token) throws MavenServerIndexerException {
    MavenServerUtil.checkToken(token);
    try {
      IndexingContext context = getIndex(indexId);
      ArrayList<AddArtifactResponse> results = new ArrayList<>();
      synchronized (context) {
        for (Path artifactFile : artifactFiles) {
          ArtifactContext artifactContext = myArtifactContextProducer.getArtifactContext(context, artifactFile.toFile());
          IndexedMavenId id = null;
          if (artifactContext != null) {
            myIndexer.addArtifactToIndex(artifactContext, context);
            //invalidateSearchersAndReadersCache
            ArtifactInfo a = artifactContext.getArtifactInfo();
            id = new IndexedMavenId(a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getPackaging(), a.getDescription());
          }
          results.add(new AddArtifactResponse(artifactFile.toFile(), id));
        }
      }
      return results;
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  @Override
  public HashSet<MavenArtifactInfo> search(MavenIndexId indexId, String pattern, int maxResult, MavenToken token)
    throws MavenServerIndexerException {
    MavenServerUtil.checkToken(token);
    try {
      IndexingContext context = getIndex(indexId);

      BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
      Query query = StringUtils.isEmpty(pattern) ? new MatchAllDocsQuery() : getWildcardQuery(pattern);
      IndexSearcher searcher = context.acquireIndexSearcher();
      TopDocs docs = searcher.search(query, maxResult);

      if (docs == null || docs.scoreDocs.length == 0) return new HashSet<>();

      HashSet<MavenArtifactInfo> result = new HashSet<>();

      for (int i = 0; i < docs.scoreDocs.length; i++) {
        int docIndex = docs.scoreDocs[i].doc;
        Document doc = searcher.getIndexReader().document(docIndex);
        ArtifactInfo a = IndexUtils.constructArtifactInfo(doc, context);
        if (a == null) continue;


        a.setRepository(getRepositoryPathOrUrl(context));
        result.add(new MavenArtifactInfo(a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getPackaging(), a.getClassifier(),
                                         a.getClassNames(), a.getRepository()));
      }
      return result;
    }
    catch (Exception e) {
      throw new MavenServerIndexerException(wrapException(e));
    }
  }

  @Override
  public HashSet<MavenArchetype> getInternalArchetypes(MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      return getInternalArchetypes();
    }
    catch (ComponentLookupException e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private HashSet<MavenArchetype> getInternalArchetypes()
    throws RemoteException, ComponentLookupException {
    HashSet<MavenArchetype> result = new HashSet<>();

    ArchetypeManager manager = myContainer.lookup(ArchetypeManager.class);
    ArchetypeCatalog internalCatalog = manager.getInternalCatalog();

    for (Archetype archetype : internalCatalog.getArchetypes()) {
      result.add(new MavenArchetype(archetype.getGroupId(),
                                    archetype.getArtifactId(),
                                    archetype.getVersion(),
                                    archetype.getRepository(),
                                    archetype.getDescription()));
    }
    return result;
  }

  @Override
  public void release(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      synchronized (myContexts) {
        for (IndexingContext indexingContext : myContexts.values()) {
          indexingContext.close(false);
        }
        myContexts.clear();
      }
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private static String getRepositoryPathOrUrl(IndexingContext index) {
    File file = index.getRepository();
    return file == null ? index.getRepositoryUrl() : file.getPath();
  }

  private static void updateIndicatorStatus(MavenServerProgressIndicator indicator,
                                            IndexingContext context,
                                            IndexUpdateResult updateResult,
                                            Date currentTimestamp) throws RemoteException {
    if (updateResult.isFullUpdate()) {
      indicator.setText("Index for " + context.getRepositoryUrl() + " updated");
    }
    else if (updateResult.getTimestamp() != null && currentTimestamp != null && updateResult.getTimestamp().equals(currentTimestamp)) {
      indicator.setText("Index for " + context.getRepositoryUrl() + "is up to date!");
    }
    else {
      indicator.setText("Index for " + context.getRepositoryUrl() + " incrementally updated");
    }
  }

  @NotNull
  private static WildcardQuery getWildcardQuery(String pattern) {
    return new WildcardQuery(new Term(SEARCH_TERM_CLASS_NAMES, "*/" + pattern.replaceAll("\\.", "/")));
  }

  private static class MyScanningListener implements ArtifactScanningListener {
    private final MavenServerProgressIndicator p;
    private AtomicInteger scanned = new AtomicInteger();

    MyScanningListener(MavenServerProgressIndicator indicator) {
      p = indicator;
    }

    @Override
    public void scanningStarted(IndexingContext ctx) {
      try {
        if (p.isCanceled()) throw new MavenProcessCanceledRuntimeException();
        p.setText("Maven Indexing Started");
      }
      catch (RemoteException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void scanningFinished(IndexingContext ctx, ScanningResult result) {
      try {
        if (p.isCanceled()) throw new MavenProcessCanceledRuntimeException();
        p.setText("Maven Indexing Finished: " + result.getTotalFiles() + " was scanned");
      }
      catch (RemoteException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void artifactError(ArtifactContext ac, Exception e) {
    }

    @Override
    public void artifactDiscovered(ArtifactContext ac) {
      try {
        if (p.isCanceled()) throw new MavenProcessCanceledRuntimeException();
        if (scanned.incrementAndGet() % 100 == 0) {
          p.setText("Scanned " + scanned.get() + " artifacts...");
        }
      }
      catch (RemoteException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
