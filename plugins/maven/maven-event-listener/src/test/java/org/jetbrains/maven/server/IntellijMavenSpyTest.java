// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.maven.server;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntellijMavenSpyTest {
  private static final String COORD = "org.test:test:jar:1.0";

  @Test
  public void resolutionOfALocalArtifactPrintsNothing() {
    assertEquals("", print(RepositoryEvent.EventType.ARTIFACT_RESOLVING, RepositoryEvent.EventType.ARTIFACT_RESOLVED));
  }

  @Test
  public void aDownloadPrintsTheStartAndTheEnd() {
    String out = print(RepositoryEvent.EventType.ARTIFACT_DOWNLOADING, RepositoryEvent.EventType.ARTIFACT_RESOLVED);
    String[] lines = out.split("\\R");
    assertEquals(2, lines.length, out);
    assertTrue(lines[0].contains("-ARTIFACT_DOWNLOADING-[IJ]-"), out);
    assertTrue(lines[0].contains("artifactCoord=" + COORD), out);
    assertTrue(lines[1].contains("-ARTIFACT_RESOLVED-[IJ]-"), out);
    assertTrue(lines[1].contains("artifactCoord=" + COORD), out);
  }

  @Test
  public void aFailureWithoutADownloadStillPrints() {
    DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
    RepositoryEvent event = new RepositoryEvent.Builder(session, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
      .setArtifact(new DefaultArtifact(COORD))
      .setException(new IllegalStateException("boom"))
      .build();
    String out = capture(new IntellijMavenSpy(), event);
    assertTrue(out.contains("-ARTIFACT_RESOLVED-[IJ]-"), out);
    assertTrue(out.contains("error=boom"), out);
  }

  private static String print(RepositoryEvent.EventType... types) {
    DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
    RepositoryEvent[] events = new RepositoryEvent[types.length];
    for (int i = 0; i < types.length; i++) {
      events[i] = new RepositoryEvent.Builder(session, types[i]).setArtifact(new DefaultArtifact(COORD)).build();
    }
    return capture(new IntellijMavenSpy(), events);
  }

  private static String capture(IntellijMavenSpy spy, RepositoryEvent... events) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = System.out;
    try {
      System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
      for (RepositoryEvent event : events) {
        spy.onEvent(event);
      }
    }
    finally {
      System.setOut(out);
    }
    return buffer.toString(StandardCharsets.UTF_8);
  }
}
