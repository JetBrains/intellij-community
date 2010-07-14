/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.embedder;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.facade.MavenFacadeManager;
import org.jetbrains.idea.maven.facade.embedder.MavenFacadeEmbedderImpl;
import org.jetbrains.idea.maven.facade.embedder.MavenIndexFetcher;
import org.jetbrains.idea.maven.indices.MavenIndicesTestCase;
import org.sonatype.nexus.index.*;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.updater.IndexUpdateRequest;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NexusIndexerTest extends MavenIndicesTestCase {
  private MavenFacadeEmbedderImpl myEmbedder;
  private MavenCustomRepositoryHelper myRepositoryHelper;
  private NexusIndexer myIndexer;
  private IndexUpdater myUpdater;
  private File myIndexDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    updateSettingsXmlFully("<settings>" +
                           "  <mirrors>" +
                           "  </mirrors>" +
                           "</settings>");

    myRepositoryHelper = new MavenCustomRepositoryHelper(myDir, "local1_index", "local1", "remote");

    myEmbedder = MavenFacadeEmbedderImpl.create(MavenFacadeManager.convertSettings(getMavenGeneralSettings()));

    myIndexer = myEmbedder.getComponent(NexusIndexer.class);
    myUpdater = myEmbedder.getComponent(IndexUpdater.class);

    assertNotNull(myIndexer);
    assertNotNull(myUpdater);

    myIndexDir = new File(myDir, "index");
    assertNotNull(myIndexDir);
  }

  @Override
  protected void tearDown() throws Exception {
    for (IndexingContext c : myIndexer.getIndexingContexts().values()) {
      myIndexer.removeIndexingContext(c, false);
    }
    myEmbedder.release();
    super.tearDown();
  }

  public void testSeraching() throws Exception {
    addContext("local1", new File(myRepositoryHelper.getTestDataPath("local1_index")), null, null);
    assertSearchWorks();
  }

  public void testUpdatingLocal() throws Exception {
    IndexingContext c = addContext("local1", myIndexDir, new File(myRepositoryHelper.getTestDataPath("local1")), null);
    myIndexer.scan(c, new NullScanningListener());

    assertSearchWorks();
  }

  public void testDownloading() throws Exception {
    String id = "remote";
    String url = "file:///" + myRepositoryHelper.getTestDataPath("remote");
    IndexingContext c = addContext(id, myIndexDir, null, url);

    IndexUpdateRequest request = new IndexUpdateRequest(c);
    request.setResourceFetcher(new MavenIndexFetcher(id, url, myEmbedder.getComponent(WagonManager.class), new NullTransferListener()));
    myUpdater.fetchAndUpdateIndex(request);

    assertSearchWorks();
  }

  public void testAddingArtifacts() throws Exception {
    IndexingContext c = addContext("virtual", myIndexDir, null, null);

    createProjectPom("");

    ArtifactInfo ai = new ArtifactInfo(c.getRepositoryId(), "group", "id", "version", null);
    ArtifactContext a = new ArtifactContext(new File(myProjectPom.getPath()), null, null, ai, null);

    myIndexer.addArtifactToIndex(a, c);

    Query q = new TermQuery(new Term(ArtifactInfo.GROUP_ID, "group"));
    Collection<ArtifactInfo> result = myIndexer.searchFlat(ArtifactInfo.VERSION_COMPARATOR, q);

    assertEquals(1, result.size());

    ArtifactInfo found = new ArrayList<ArtifactInfo>(result).get(0);
    assertEquals("group", found.groupId);
    assertEquals("id", found.artifactId);
    assertEquals("version", found.version);

    IndexReader r = c.getIndexReader();
    for (int i = 0; i < r.numDocs(); i++) {
      Document d = r.document(i);
    }
  }

  public void testIteratingAddedArtifacts() throws Exception {
    if (ignore()) return;

    IndexingContext c = addContext("virtual", myIndexDir, null, null);

    addArtifact(c, "group1", "id1", "version1", "x:/path1");
    addArtifact(c, "group2", "id2", "version2", "x:/path2");
    addArtifact(c, "group3", "id3", "version3", "x:/path3");

    IndexReader r = c.getIndexReader();
    assertEquals(5, r.numDocs());
    List<String> result = new ArrayList<String>();
    for (int i = 0; i < r.numDocs(); i++) {
      // third document becomes deleted somehow...
      Document d = r.document(i);
      String uinfo = d.get(ArtifactInfo.UINFO);
      result.add(uinfo);
    }
    System.out.println(result);
  }

  public void testSearchingWithLucene() throws Exception {
    IndexSearcher searcher = new IndexSearcher(myRepositoryHelper.getTestDataPath("local1_index"));
    Hits result = searcher.search(new TermQuery(new Term(ArtifactInfo.GROUP_ID, "junit")));

    assertEquals(3, result.length());

    searcher.close();
  }

  public void testAddingTwoContextsWithSameId() throws Exception {
    IndexingContext i1 = addContext("id", new File(myIndexDir, "one"), null, null);
    IndexingContext i2 = addContext("id", new File(myIndexDir, "two"), null, null);

    myIndexer.removeIndexingContext(i1, false);
    myIndexer.removeIndexingContext(i2, false);

    addContext("id", new File(myIndexDir, "one"), null, null);
    addContext("id", new File(myIndexDir, "two"), null, null);
  }

  private void addArtifact(IndexingContext c, String groupId, String artifactId, String version, String path) throws IOException {
    ArtifactInfo ai = new ArtifactInfo(c.getRepositoryId(), groupId, artifactId, version, null);
    ai.size = -1;
    ai.lastModified = -1;
    ai.sourcesExists = ArtifactAvailablility.fromString(Integer.toString(0));
    ai.javadocExists = ArtifactAvailablility.fromString(Integer.toString(0));

    ArtifactContext a = new ArtifactContext(new File(path), null, null, ai, null);
    myIndexer.addArtifactToIndex(a, c);
  }

  private IndexingContext addContext(String id, File indexDir, File repoDir, String repoUrl) throws Exception {
    return myIndexer.addIndexingContext(
      id,
      id,
      repoDir,
      indexDir,
      repoUrl,
      null, // repo update url
      NexusIndexer.FULL_INDEX);
  }

  private void assertSearchWorks() throws IOException {
    WildcardQuery q = new WildcardQuery(new Term(ArtifactInfo.ARTIFACT_ID, "junit*"));
    Collection<ArtifactInfo> result = myIndexer.searchFlat(ArtifactInfo.VERSION_COMPARATOR, q);
    assertEquals(3, result.size());
  }

  private static class NullScanningListener implements ArtifactScanningListener {
    public void scanningStarted(IndexingContext indexingContext) {
    }

    public void scanningFinished(IndexingContext indexingContext, ScanningResult scanningResult) {
    }

    public void artifactError(ArtifactContext artifactContext, Exception e) {
    }

    public void artifactDiscovered(ArtifactContext artifactContext) {
    }
  }

  private static class NullTransferListener implements TransferListener {
    public void transferInitiated(TransferEvent event) {
    }

    public void transferStarted(TransferEvent event) {
    }

    public void transferProgress(TransferEvent event, byte[] bytes, int i) {
    }

    public void transferCompleted(TransferEvent event) {
    }

    public void transferError(TransferEvent event) {
    }

    public void debug(String s) {
    }
  }
}
