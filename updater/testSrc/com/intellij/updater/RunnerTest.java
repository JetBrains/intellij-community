package com.intellij.updater;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class RunnerTest extends UpdaterTestCase {
  @Test
  public void testExtractingFiles() throws Exception {
    String[] args = {"bar", "ignored=xxx;yyy;zzz/zzz", "critical=", "ignored=aaa", "baz", "critical=ccc"};
    Runner.initLogger();

    assertEquals(Arrays.asList("xxx", "yyy", "zzz/zzz", "aaa"),
                 Runner.extractArguments(args, "ignored"));

    assertEquals(Arrays.asList("ccc"),
                 Runner.extractArguments(args, "critical"));

    assertEquals(Collections.<String>emptyList(),
                 Runner.extractArguments(args, "unknown"));
  }
}
