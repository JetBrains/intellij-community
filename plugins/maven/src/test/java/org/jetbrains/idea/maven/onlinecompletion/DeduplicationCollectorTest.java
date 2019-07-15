// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

public class DeduplicationCollectorTest extends UsefulTestCase {

  public void testLocalBeforeRemote() {
    MavenDependencyCompletionItem local = new MavenDependencyCompletionItem("group:artifact:1", MavenDependencyCompletionItem.Type.LOCAL);
    MavenDependencyCompletionItem remote = new MavenDependencyCompletionItem("group:artifact:1", MavenDependencyCompletionItem.Type.REMOTE);
    Stream<List<MavenDependencyCompletionItem>> stream = Arrays.stream(new List[]{
      asList(local),
      asList(remote)}
    );

    List<MavenDependencyCompletionItem> result =
      stream.flatMap(Collection::stream).collect(new DeduplicationCollector<>(m -> m.getDisplayString()));
    assertSize(1, result);
    assertEquals(MavenDependencyCompletionItem.Type.LOCAL, result.get(0).getType());
  }

  public void testRemoteBeforeLocal() {
    MavenDependencyCompletionItem local = new MavenDependencyCompletionItem("group:artifact:1", MavenDependencyCompletionItem.Type.LOCAL);
    MavenDependencyCompletionItem remote = new MavenDependencyCompletionItem("group:artifact:1", MavenDependencyCompletionItem.Type.REMOTE);
    Stream<List<MavenDependencyCompletionItem>> stream = Arrays.stream(new List[]{
      asList(remote),
      asList(local)}
    );

    List<MavenDependencyCompletionItem> result =
      stream.flatMap(Collection::stream).collect(new DeduplicationCollector<>(m -> m.getDisplayString()));
    assertSize(1, result);
    assertEquals(MavenDependencyCompletionItem.Type.LOCAL, result.get(0).getType());
  }

  public void testShowLocalVersions() {
    MavenDependencyCompletionItem local = new MavenDependencyCompletionItem("group:artifact:1", MavenDependencyCompletionItem.Type.LOCAL);
    MavenDependencyCompletionItem remote = new MavenDependencyCompletionItem("group:artifact:2", MavenDependencyCompletionItem.Type.REMOTE);
    Stream<List<MavenDependencyCompletionItem>> stream = Arrays.stream(new List[]{
      asList(remote),
      asList(local)}
    );

    List<MavenDependencyCompletionItem> result =
      stream.flatMap(Collection::stream).collect(new DeduplicationCollector<>(m -> m.getGroupId() + ":" + m.getArtifactId()));
    assertSize(1, result);
    assertEquals(MavenDependencyCompletionItem.Type.LOCAL, result.get(0).getType());
  }

  public void testShowBothVersions() {
    MavenDependencyCompletionItem local = new MavenDependencyCompletionItem("group:artifact:1", MavenDependencyCompletionItem.Type.LOCAL);
    MavenDependencyCompletionItem remote = new MavenDependencyCompletionItem("group:artifact:2", MavenDependencyCompletionItem.Type.REMOTE);
    Stream<List<MavenDependencyCompletionItem>> stream = Arrays.stream(new List[]{
      asList(remote),
      asList(local)}
    );

    List<MavenDependencyCompletionItem> result =
      stream.flatMap(Collection::stream).collect(new DeduplicationCollector<>(m -> m.getDisplayString()));
    assertSize(2, result);
  }
}