package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.jetbrains.idea.svn.integrate.GroupSplitter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

public class SvnGroupSplitterTest extends TestCase {
  public void testSimple() throws Exception {
    final CommittedChangeList[] arr = {create(1), create(2), create(3), create(4), create(5)};
    final GroupSplitter splitter = new GroupSplitter(Arrays.asList(arr));

    final int[] expected = {5};
    checkExpected(splitter, expected);
  }

  public void testSeparate() throws Exception {
    final CommittedChangeList[] arr = {create(1), create(3), create(5), create(7), create(9)};
    final GroupSplitter splitter = new GroupSplitter(Arrays.asList(arr));

    final int[] expected = {1,1,1,1,1};
    checkExpected(splitter, expected);
  }

  public void testNothing() throws Exception {
    final CommittedChangeList[] arr = {};
    final GroupSplitter splitter = new GroupSplitter(Arrays.asList(arr));
    Assert.assertFalse(splitter.hasNext());
  }

  public void testLongPackAtTheEnd() throws Exception {
    final CommittedChangeList[] arr = {create(1), create(3), create(5), create(6), create(7)};
    final GroupSplitter splitter = new GroupSplitter(Arrays.asList(arr));

    final int[] expected = {1,1,3};
    checkExpected(splitter, expected);
  }

  public void testShortAtTheEnd() throws Exception {
    final CommittedChangeList[] arr = {create(1), create(2), create(3), create(4), create(8)};
    final GroupSplitter splitter = new GroupSplitter(Arrays.asList(arr));

    final int[] expected = {4,1};
    checkExpected(splitter, expected);
  }

  public void testMixed() throws Exception {
    final CommittedChangeList[] arr = {create(1), create(2), create(4), create(6), create(7), create(8), create(9), create(11),
      create(13), create(14), create(122)};
    final GroupSplitter splitter = new GroupSplitter(Arrays.asList(arr));

    final int[] expected = {2,1,4,1,2,1};
    checkExpected(splitter, expected);
  }

  private void checkExpected(GroupSplitter splitter, int[] expected) {
    int i = 0;
    while (splitter.hasNext()) {
      Assert.assertTrue(i < expected.length);
      final int step = splitter.step();
      Assert.assertEquals(step, expected[i]);
      ++ i;
    }
  }

  private CommittedChangeList create(final long number) {
    return new CommittedChangeListImpl("abc", "bca", "committer", number, new Date(), Collections.<Change>emptyList());
  }
}
