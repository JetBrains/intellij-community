package org.jetbrains.idea.maven.repository;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.WildcardQuery;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.codehaus.plexus.PlexusContainer;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.core.MavenFactory;
import org.sonatype.nexus.index.ArtifactContext;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.ArtifactScanningListener;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.context.IndexContextInInconsistentStateException;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.scan.ScanningResult;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class NexusIndexerTest extends MavenTestCase {
  private MavenWithDataTestFixture myDataTestFixture;
  private MavenEmbedder embedder;
  private NexusIndexer indexer;
  private IndexUpdater updater;
  private File indexDir;

  @Override
  protected void setUpFixtures() throws Exception {
    super.setUpFixtures();
    myDataTestFixture = new MavenWithDataTestFixture(new File(myTempDirFixture.getTempDirPath()));
    myDataTestFixture.setUp();
  }

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    embedder = MavenFactory.createEmbedderForExecute(getMavenCoreSettings());

    PlexusContainer p = embedder.getPlexusContainer();
    indexer = (NexusIndexer)p.lookup(NexusIndexer.class);
    updater = (IndexUpdater)p.lookup(IndexUpdater.class);

    assertNotNull(indexer);
    assertNotNull(updater);

    indexDir = new File(myDir, "index");
    assertNotNull(indexDir);
  }

  @Override
  protected void tearDown() throws Exception {
    for (IndexingContext c : indexer.getIndexingContexts().values()) {
      indexer.removeIndexingContext(c, false);
    }
    embedder.stop();
    super.tearDown();
  }

  public void testSeraching() throws Exception {
    addContext("local1", new File(myDataTestFixture.getTestDataPath("local1_index")), null, null);
    assertSearchWorks();
  }

  public void testCreating() throws Exception {
    IndexingContext c = addContext("local1", indexDir, new File(myDataTestFixture.getTestDataPath("local1")), null);
    indexer.scan(c, new NullScanningListener());

    assertSearchWorks();
  }

  public void testDownloading() throws Exception {
    IndexingContext c = addContext("remote", indexDir, null, "file:///" + myDataTestFixture.getTestDataPath("remote"));
    updater.fetchAndUpdateIndex(c, new NullTransferListener());

    assertSearchWorks();
  }

  private IndexingContext addContext(String id, File indexDir, File repoDir, String repoUrl) throws Exception {
    return indexer.addIndexingContext(
        id,
        id,
        repoDir,
        indexDir,
        repoUrl,
        null, // repo update url
        NexusIndexer.FULL_INDEX);
  }

  private void assertSearchWorks() throws IOException, IndexContextInInconsistentStateException {
    WildcardQuery q = new WildcardQuery(new Term(ArtifactInfo.ARTIFACT_ID, "junit*"));
    Collection<ArtifactInfo> result = indexer.searchFlat(ArtifactInfo.VERSION_COMPARATOR, q);
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
