package com.intellij.util.diff;

import junit.framework.TestCase;

/**
 * @author dyoma
 */
public class LinkedDiffPathsTest extends TestCase {
  protected LinkedDiffPaths createPaths(int maxX, int maxY) {
    return new LinkedDiffPaths(maxX, maxY);
  }

  public void testOneDeleteAtferEnd() {
    LinkedDiffPaths paths = createPaths(2, 3);
    int key = paths.encodeStep(1, 1, 2, false, -1);
    paths.encodeStep(1, 2, 0, true, key);
    Diff.Change change = decode(paths);
    IntLCSTest.checkLastChange(change, 2, 2, 1, 0);
  }

  public void testOneInsertedAtBegging() {
    LinkedDiffPaths paths = createPaths(3, 2);
    paths.encodeStep(2, 1, 2, false, -1);
    Diff.Change change = decode(paths);
    IntLCSTest.checkLastChange(change, 0, 0, 0, 1);
  }

  public void testSingleMiddleChange() {
    LinkedDiffPaths paths = createPaths(3, 3);
    int key = paths.encodeStep(0, 0, 1, true, -1);
    key = paths.encodeStep(1, 0, 0, false, key);
    paths.encodeStep(2, 2, 1, true, key);
    IntLCSTest.checkLastChange(decode(paths), 1, 1, 1, 1);
  }

  public void testSingleChangeAtEnd() {
    LinkedDiffPaths paths = createPaths(2, 2);
    int key = paths.encodeStep(0, 0, 1, false, -1);
    key = paths.encodeStep(0, 1, 0, true, key);
    paths.encodeStep(1, 1, 0, false, key);
    IntLCSTest.checkLastChange(decode(paths), 1, 1, 1, 1);
  }

  public void testNotSquareChangeAtEnd() {
    LinkedDiffPaths paths = createPaths(2, 3);
    int key = paths.encodeStep(0, 0, 1, false, -1);
    key = paths.encodeStep(0, 1, 0, true, key);
    key = paths.encodeStep(0, 2, 0, true, key);
    paths.encodeStep(1, 2, 0, false, key);
    IntLCSTest.checkLastChange(decode(paths), 1, 1, 2, 1);
  }

  private Diff.Change decode(LinkedDiffPaths paths) {
    Reindexer reindexer = new Reindexer();
    reindexer.idInit(paths.getXSize(), paths.getYSize());
    Diff.ChangeBuilder builder = new Diff.ChangeBuilder();
    reindexer.reindex(paths, builder);
    return builder.getFirstChange();
  }
}
