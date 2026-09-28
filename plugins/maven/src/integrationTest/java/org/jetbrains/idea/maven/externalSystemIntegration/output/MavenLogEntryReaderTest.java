// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MavenLogEntryReaderTest {

  @Test
  public void testParser() {
    MavenLogEntryReader.MavenLogEntry entry = new MavenLogEntryReader.MavenLogEntry("[ERROR] error line");
    assertEquals(LogMessageType.ERROR, entry.myType);
    assertEquals("error line", entry.myLine);

    entry = new MavenLogEntryReader.MavenLogEntry("[INFO] info line");
    assertEquals(LogMessageType.INFO, entry.myType);
    assertEquals("info line", entry.myLine);

    entry = new MavenLogEntryReader.MavenLogEntry("[WARNING] warning line");
    assertEquals(LogMessageType.WARNING, entry.myType);
    assertEquals("warning line", entry.myLine);

    entry = new MavenLogEntryReader.MavenLogEntry("line");
    assertNull(entry.myType);
    assertEquals("line", entry.myLine);
  }

  @Test
  public void testRemoveProgressFromOutput() {
    MavenLogEntryReader.MavenLogEntry entry = new MavenLogEntryReader.MavenLogEntry("Progress 1\r Progress 2\r Progress 3\r[INFO] Done");
    assertEquals(LogMessageType.INFO, entry.myType);
    assertEquals("Done", entry.myLine);
  }
}
