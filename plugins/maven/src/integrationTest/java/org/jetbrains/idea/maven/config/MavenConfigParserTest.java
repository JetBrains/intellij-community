// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MavenConfigParserTest {
  @TempDir
  Path myDir;

  @Test
  public void testParseShortNames() throws IOException {
    writeMavenConfig("-o -U -N -T3 -q -X -e -C -c -ff -fae -fn" +
                     " -s user-settings.xml -gs global-settings.xml");
    MavenConfig config = MavenConfigParser.parse(myDir.toString());
    assertTrue(config.hasOption(MavenConfigSettings.OFFLINE));
    assertTrue(config.hasOption(MavenConfigSettings.UPDATE_SNAPSHOTS));
    assertTrue(config.hasOption(MavenConfigSettings.NON_RECURSIVE));
    assertTrue(config.hasOption(MavenConfigSettings.QUIET));
    assertTrue(config.hasOption(MavenConfigSettings.ERRORS));
    assertTrue(config.hasOption(MavenConfigSettings.DEBUG));
    assertTrue(config.hasOption(MavenConfigSettings.CHECKSUM_WARNING_POLICY));
    assertTrue(config.hasOption(MavenConfigSettings.CHECKSUM_FAILURE_POLICY));
    assertTrue(config.hasOption(MavenConfigSettings.FAIL_AT_END));
    assertTrue(config.hasOption(MavenConfigSettings.FAIL_FAST));
    assertTrue(config.hasOption(MavenConfigSettings.FAIL_NEVER));
    assertEquals("3", config.getOptionValue(MavenConfigSettings.THREADS));
    assertEquals("user-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_USER_SETTINGS));
    assertEquals("global-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS));
  }

  @Test
  public void testParseLongNames() throws IOException {
    writeMavenConfig("--offline --update-snapshots --non-recursive --quiet --debug --errors --strict-checksums " +
                     "--lax-checksums --fail-fast --fail-at-end --fail-never --threads 3 " +
                     "--settings user-settings.xml --global-settings global-settings.xml");
    MavenConfig config = MavenConfigParser.parse(myDir.toString());
    assertTrue(config.hasOption(MavenConfigSettings.OFFLINE));
    assertTrue(config.hasOption(MavenConfigSettings.UPDATE_SNAPSHOTS));
    assertTrue(config.hasOption(MavenConfigSettings.NON_RECURSIVE));
    assertTrue(config.hasOption(MavenConfigSettings.QUIET));
    assertTrue(config.hasOption(MavenConfigSettings.ERRORS));
    assertTrue(config.hasOption(MavenConfigSettings.DEBUG));
    assertTrue(config.hasOption(MavenConfigSettings.CHECKSUM_WARNING_POLICY));
    assertTrue(config.hasOption(MavenConfigSettings.CHECKSUM_FAILURE_POLICY));
    assertTrue(config.hasOption(MavenConfigSettings.FAIL_AT_END));
    assertTrue(config.hasOption(MavenConfigSettings.FAIL_FAST));
    assertTrue(config.hasOption(MavenConfigSettings.FAIL_NEVER));
    assertEquals("3", config.getOptionValue(MavenConfigSettings.THREADS));
    assertEquals("user-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_USER_SETTINGS));
    assertEquals("global-settings.xml", config.getOptionValue(MavenConfigSettings.ALTERNATE_GLOBAL_SETTINGS));
  }

  @Test
  public void testParseJavaOptions() throws IOException {
    writeMavenConfig("-Dkey1=value -Dkey2=\"value with spaces\" " +
                     "\"-Dkey3=another value with spaces\" -Dkey4");
    MavenConfig config = MavenConfigParser.parse(myDir.toString());
    assertEquals("value", config.getJavaProperties().get("key1"));
    assertEquals("value with spaces", config.getJavaProperties().get("key2"));
    assertEquals("another value with spaces", config.getJavaProperties().get("key3"));
    assertEquals("", config.getJavaProperties().get("key4"));
    assertNull(config.getJavaProperties().get("key"));
  }

  @Test
  public void testParseJavaOptionsTogetherWithMaven() throws IOException {
    writeMavenConfig("-Dkey1=value -Dkey2=\"value with spaces\"  \"-Dkey3=another value with spaces\" -Dkey4 --offline --threads 3");
    MavenConfig config = MavenConfigParser.parse(myDir.toString());
    assertEquals("value", config.getJavaProperties().get("key1"));
    assertEquals("value with spaces", config.getJavaProperties().get("key2"));
    assertEquals("another value with spaces", config.getJavaProperties().get("key3"));
    assertEquals("", config.getJavaProperties().get("key4"));
    assertNull(config.getJavaProperties().get("key"));
    assertTrue(config.hasOption(MavenConfigSettings.OFFLINE));
    assertEquals("3", config.getOptionValue(MavenConfigSettings.THREADS));
  }

  @Test
  public void testUnknownNames() throws IOException {
    writeMavenConfig("-unknown -ZZ --badprop");
    MavenConfig config = MavenConfigParser.parse(myDir.toString());
    assertTrue(config.isEmpty());
  }

  private void writeMavenConfig(String content) throws IOException {
    Path configFile = myDir.resolve(".mvn/maven.config");
    Files.createDirectories(configFile.getParent());
    Files.writeString(configFile, content);
  }
}
