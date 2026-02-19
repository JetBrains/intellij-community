// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import org.jetbrains.idea.maven.server.ParallelRunnerForServer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ParallelRunnerForServerTest {
  @Test
  public void testSequential() {
    var in = List.of(1, 2, 3, 4, 5);
    var out = new ArrayList<Integer>();
    ParallelRunnerForServer.execute(false, in, out::add);
    assertEquals(in, out);
  }

  @Test
  public void testSequentialRethrow() {
    Exception rethrown = null;
    var text = "should be rethrown";
    var in = List.of(1, 2, 3, 4, 5);
    try {
      ParallelRunnerForServer.<Integer, Integer>execute(false, in, it -> {
        throw new RuntimeException(text);
      });
    } catch (Exception e) {
      rethrown = e;
    }
    assertNotNull(rethrown);
    assertEquals(text, rethrown.getMessage());
  }

  @Test(expected = RuntimeException.class)
  public void testSequentialSneakyRethrow() {
    var text = "should be rethrown";
    var in = List.of(1, 2, 3, 4, 5);
    ParallelRunnerForServer.execute(false, in, it -> {
      throw new RuntimeException(text);
    });
  }

  @Test
  public void testParallel() {
    var in = Set.of(1, 2, 3, 4, 5);
    var out = new ConcurrentHashMap<Integer, Integer>();
    ParallelRunnerForServer.execute(true, in, it -> out.put(it, it));
    assertEquals(in, out.keySet());
  }

  @Test
  public void testParallelRethrowRuntimeException() {
    Exception rethrown = null;
    var text = "should be rethrown";
    var in = List.of(1, 2, 3, 4, 5);
    try {
      ParallelRunnerForServer.execute(true, in, it -> {
        throw new RuntimeException(text);
      });
    } catch (RuntimeException e) {
      rethrown = e;
    }
    assertNotNull(rethrown);
    assertEquals(text, rethrown.getMessage());
  }

  @Test
  public void testParallelRethrow() {
    Exception rethrown = null;
    var text = "should be rethrown";
    var in = List.of(1, 2, 3, 4, 5);
    try {
      ParallelRunnerForServer.<Integer, Integer>execute(true, in, it -> {
        throw new RuntimeException(text);
      });
    } catch (RuntimeException e) {
      rethrown = e;
    }
    assertNotNull(rethrown);
    assertEquals(text, rethrown.getMessage());
  }

  @Test(expected = RuntimeException.class)
  public void testParallelSneakyThrow() {
    var text = "should be rethrown";
    var in = List.of(1, 2, 3, 4, 5);
    ParallelRunnerForServer.execute(true, in, it -> {
      throw new RuntimeException(text);
    });
  }

}
