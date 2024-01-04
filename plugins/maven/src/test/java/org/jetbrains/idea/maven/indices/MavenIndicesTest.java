// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.idea.maven.model.RepositoryKind;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.server.MavenIndexerWrapper;
import org.junit.Assert;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.jetbrains.idea.maven.indices.MavenIndices.LOCAL_REPOSITORY_ID;

public class MavenIndicesTest extends CodeInsightFixtureTestCase {

  private MavenIndices.RepositoryDiff<MavenIndex> localDiff;
  private MavenIndices.RepositoryDiff<List<MavenIndex>> remoteDiff;
  private MavenIndices.RepositoryDiffContext myContext;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addFileToProject("Indices/Index0/index.properties",
                               """
                                 #Sun Oct 31 18:51:24 MSK 2021
                                 dataDirName=data0
                                 kind=REMOTE
                                 id=central
                                 pathOrUrl=https://repo.maven.apache.org/maven2
                                 version=5""").getVirtualFile();

    myFixture.addFileToProject("Indices/Index1/index.properties",
                               """
                                 #Sun Oct 31 18:51:24 MSK 2021
                                 dataDirName=data0
                                 kind=REMOTE
                                 id=snapshot
                                 pathOrUrl=https://repo.maven.apache.org/snapshot
                                 version=5""").getVirtualFile();

    myFixture.addFileToProject("Indices/Index2/index.properties",
                               """
                                 #Sun Oct 31 18:51:26 MSK 2021
                                 dataDirName=data2
                                 kind=LOCAL
                                 lastUpdate=1635507096543
                                 id=local
                                 pathOrUrl=/home/user/.m2/repository
                                 version=5""").getVirtualFile();

    VirtualFile localM3 = myFixture
      .addFileToProject("Indices/Index3/index.properties",
                        """
                          #Sun Oct 31 18:51:26 MSK 2021
                          dataDirName=data2
                          kind=LOCAL
                          lastUpdate=1635507096543
                          id=local
                          pathOrUrl=/home/user/.m3/repository
                          version=5""").getVirtualFile();

    myContext = new MavenIndices.RepositoryDiffContext(
      Mockito.mock(MavenIndexerWrapper.class),
      localM3.getParent().getParent().toNioPath().toFile());
  }

  @Override
  protected void tearDown() throws Exception {
    RunAll.runAll(
      () -> super.tearDown(),
      () -> {
        if (localDiff != null && localDiff.oldIndices != null) localDiff.oldIndices.close(false);
      },
      () -> {
        if (localDiff != null && localDiff.newIndices != null) localDiff.newIndices.close(false);
      },
      () -> {
        if (remoteDiff != null) remoteDiff.oldIndices.forEach(i -> i.close(false));
      },
      () -> {
        if (remoteDiff != null) remoteDiff.newIndices.forEach(i -> i.close(false));
      }
    );
  }

  public void testGetLocalInitAndNoDiff() {
    MavenRepositoryInfo localRepo = new MavenRepositoryInfo(LOCAL_REPOSITORY_ID, "/home/user/.m2/repository", RepositoryKind.LOCAL);
    localDiff = MavenIndices.getLocalDiff(localRepo, myContext, null);
    Assert.assertNotNull(localDiff.newIndices);
    Assert.assertEquals(localRepo.getUrl(), localDiff.newIndices.getRepositoryPathOrUrl());
    Assert.assertNull(localDiff.oldIndices);

    localDiff = MavenIndices.getLocalDiff(localRepo, myContext, localDiff.newIndices);
    Assert.assertNotNull(localDiff.newIndices);
    Assert.assertEquals(localRepo.getUrl(), localDiff.newIndices.getRepositoryPathOrUrl());
    Assert.assertNull(localDiff.oldIndices);
  }

  public void testGetLocalInitAndDiff() {
    MavenRepositoryInfo localRepo = new MavenRepositoryInfo(LOCAL_REPOSITORY_ID, "/home/user/.m2/repository", RepositoryKind.LOCAL);
    localDiff = MavenIndices.getLocalDiff(localRepo, myContext, null);
    Assert.assertNotNull(localDiff.newIndices);
    Assert.assertEquals(localRepo.getUrl(), localDiff.newIndices.getRepositoryPathOrUrl());
    Assert.assertNull(localDiff.oldIndices);

    localRepo = new MavenRepositoryInfo(LOCAL_REPOSITORY_ID, "/home/user/.m3/repository", RepositoryKind.LOCAL);
    MavenIndex currentLocalIndex = localDiff.newIndices;
    localDiff = MavenIndices.getLocalDiff(localRepo, myContext, currentLocalIndex);
    Assert.assertNotNull(localDiff.newIndices);
    Assert.assertEquals(localRepo.getUrl(), localDiff.newIndices.getRepositoryPathOrUrl());
    Assert.assertSame(currentLocalIndex, localDiff.oldIndices);
  }

  public void testGetLocalCreateNew() {
    MavenRepositoryInfo localRepo = new MavenRepositoryInfo(LOCAL_REPOSITORY_ID, "/home/user/.m4/repository", RepositoryKind.LOCAL);
    localDiff = MavenIndices.getLocalDiff(localRepo, myContext, null);
    Assert.assertNotNull(localDiff.newIndices);
    Assert.assertEquals(localRepo.getUrl(), localDiff.newIndices.getRepositoryPathOrUrl());
    Assert.assertNull(localDiff.oldIndices);
  }

  public void testGetRemoteInitAndNoDiff() {
    MavenRepositoryInfo remoteRepo = new MavenRepositoryInfo("central", "https://repo.maven.apache.org/maven2", RepositoryKind.LOCAL);
    Map<String, Set<String>> remoteRepositoryIdsByUrl = Map.of(remoteRepo.getUrl(), Collections.singleton(remoteRepo.getId()));
    remoteDiff = MavenIndices.getRemoteDiff(remoteRepositoryIdsByUrl, Collections.emptyList(), myContext);
    Assert.assertEquals(1, remoteDiff.newIndices.size());
    Assert.assertEquals(remoteRepo.getUrl(), remoteDiff.newIndices.get(0).getRepositoryPathOrUrl());
    Assert.assertTrue(remoteDiff.oldIndices.isEmpty());

    remoteDiff = MavenIndices.getRemoteDiff(remoteRepositoryIdsByUrl, remoteDiff.newIndices, myContext);
    Assert.assertEquals(1, remoteDiff.newIndices.size());
    Assert.assertEquals(remoteRepo.getUrl(), remoteDiff.newIndices.get(0).getRepositoryPathOrUrl());
    Assert.assertTrue(remoteDiff.oldIndices.isEmpty());
  }

  public void testGetRemoteInitAndDiff() {
    MavenRepositoryInfo remoteRepo = new MavenRepositoryInfo("central", "https://repo.maven.apache.org/maven2", RepositoryKind.LOCAL);
    Map<String, Set<String>> remoteRepositoryIdsByUrl = Map.of(remoteRepo.getUrl(), Collections.singleton(remoteRepo.getId()));
    remoteDiff = MavenIndices.getRemoteDiff(remoteRepositoryIdsByUrl, Collections.emptyList(), myContext);
    Assert.assertEquals(1, remoteDiff.newIndices.size());
    Assert.assertEquals(remoteRepo.getUrl(), remoteDiff.newIndices.get(0).getRepositoryPathOrUrl());
    Assert.assertTrue(remoteDiff.oldIndices.isEmpty());

    remoteRepo = new MavenRepositoryInfo("snapshot", "https://repo.maven.apache.org/snapshot", RepositoryKind.REMOTE);
    remoteRepositoryIdsByUrl = Map.of(remoteRepo.getUrl(), Collections.singleton(remoteRepo.getId()));
    remoteDiff = MavenIndices.getRemoteDiff(remoteRepositoryIdsByUrl, remoteDiff.newIndices, myContext);
    Assert.assertEquals(1, remoteDiff.newIndices.size());
    Assert.assertEquals(remoteRepo.getUrl(), remoteDiff.newIndices.get(0).getRepositoryPathOrUrl());
    Assert.assertEquals(1, remoteDiff.oldIndices.size());
    Assert.assertTrue(remoteDiff.oldIndices.get(0).getRepositoryPathOrUrl().contains("maven2"));
  }

  public void testGetRemoteCreateNew() {
    MavenRepositoryInfo remoteRepo = new MavenRepositoryInfo("milestone", "https://repo.maven.apache.org/milestone", RepositoryKind.REMOTE);
    Map<String, Set<String>> remoteRepositoryIdsByUrl = Map.of(remoteRepo.getUrl(), Collections.singleton(remoteRepo.getUrl()));
    remoteDiff = MavenIndices.getRemoteDiff(remoteRepositoryIdsByUrl, Collections.emptyList(), myContext);
    Assert.assertEquals(1, remoteDiff.newIndices.size());
    Assert.assertEquals(remoteRepo.getUrl(), remoteDiff.newIndices.get(0).getRepositoryPathOrUrl());
    Assert.assertTrue(remoteDiff.oldIndices.isEmpty());
  }

  public void testGetRemoteDiffWithDuplicates() {
    myFixture.addFileToProject("Indices/Index10/index.properties",
                               """
                                 #Sun Oct 31 18:51:24 MSK 2021
                                 dataDirName=data0
                                 kind=REMOTE
                                 id=central
                                 pathOrUrl=https://repo.maven.apache.org/maven2
                                 version=5""").getVirtualFile();

    MavenRepositoryInfo remoteRepo = new MavenRepositoryInfo("central", "https://repo.maven.apache.org/maven2", RepositoryKind.REMOTE);
    Map<String, Set<String>> remoteRepositoryIdsByUrl = Map.of(remoteRepo.getUrl(), Collections.singleton(remoteRepo.getId()));
    remoteDiff = MavenIndices.getRemoteDiff(remoteRepositoryIdsByUrl, Collections.emptyList(), myContext);
    Assert.assertEquals(1, remoteDiff.newIndices.size());
    Assert.assertEquals(remoteRepo.getUrl(), remoteDiff.newIndices.get(0).getRepositoryPathOrUrl());
    Assert.assertTrue(remoteDiff.oldIndices.isEmpty());
  }

  public void testGroupRemoteRepositoriesByUrl() {
    MavenRemoteRepository remote1 = new MavenRemoteRepository("id1", "name", "http://foo/bar", null, null, null);
    MavenRemoteRepository remote2 = new MavenRemoteRepository("id2", "name", "  http://foo\\bar\\\\  ", null, null, null);
    MavenRemoteRepository remote3 = new MavenRemoteRepository("id3", "name", "http://foo\\bar\\baz", null, null, null);
    MavenRemoteRepository remote4 = new MavenRemoteRepository("id4", "name", "http://foo/bar", null, null, null);
    MavenRemoteRepository remote5 = new MavenRemoteRepository("id4", "name", "http://foo/baz", null, null, null);

    assertEquals(1, MavenIndexUtils.groupRemoteRepositoriesByUrl(Collections.singleton(remote1)).size());
    assertEquals(1, MavenIndexUtils.groupRemoteRepositoriesByUrl(asList(remote1, remote2)).size());
    assertEquals(2, MavenIndexUtils.groupRemoteRepositoriesByUrl(asList(remote1, remote2, remote3)).size());
    assertEquals(2, MavenIndexUtils.groupRemoteRepositoriesByUrl(asList(remote1, remote2, remote3, remote4)).size());
    assertEquals(3, MavenIndexUtils.groupRemoteRepositoriesByUrl(asList(remote1, remote2, remote3, remote4, remote5)).size());
  }
}