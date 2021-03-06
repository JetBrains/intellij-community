// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.committed.ChangesBunch;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.history.*;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.reverse;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class SvnCachingRevisionsTest extends CodeInsightFixtureTestCase {
  private final static Url URL = SvnUtil.parseUrl("file:///C:/repo/trunk");
  private final static Url ROOT = SvnUtil.parseUrl("file:///C:/repo");
  private final static String AUTHOR = "author";
  private final static int PAGE = 5;

  private SvnVcs myVcs;
  private SvnRepositoryLocation myLocation;
  private LoadedRevisionsCache myInternalManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myVcs = SvnVcs.getInstance(getProject());
    myLocation = new SvnRepositoryLocation(URL);
    myInternalManager = LoadedRevisionsCache.getInstance(myFixture.getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myInternalManager = null;
      myLocation = null;
      myVcs = null;
      FileUtil.delete(SvnApplicationSettings.getLoadedRevisionsDir(myFixture.getProject()));
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private SvnChangeList createList(final long revision) {
    LogEntry entry =
      new LogEntry.Builder().setRevision(revision).setAuthor(AUTHOR).setDate(new Date(System.currentTimeMillis())).setMessage("").build();

    return new SvnChangeList(myVcs, myLocation, entry, ROOT);
  }

  private final class MockSvnLogLoader implements SvnLogLoader {
    private final List<Long> myRevisions;

    private MockSvnLogLoader(final List<Long> revisions) {
      myRevisions = revisions;
    }

    @Override
    public List<CommittedChangeList> loadInterval(final Revision fromIncluding, final Revision toIncluding, final int maxCount,
                                                  final boolean includingYoungest, final boolean includeOldest) {
      long young = fromIncluding.getNumber();
      young = (young == -1) ? myRevisions.get(myRevisions.size() - 1) : young;
      final long old = toIncluding.getNumber();

      final List<CommittedChangeList> result = new ArrayList<>();

      int cnt = -1;
      // from back
      for (int i = myRevisions.size() - 1; i >= 0; -- i) {
        final Long current = myRevisions.get(i);
        if ((cnt == -1) && (current <= young)) {
          cnt = 0;
        }
        if (cnt >= 0) {
          ++ cnt;
        }
        if ((young > current) || (includingYoungest && (young == current))) {
          if ((old < current) || (includeOldest && (old == current))) {
            result.add(createList(current));
          }
        }
        if (cnt == maxCount) {
          break;
        }
      }
      return result;
    }
  }

  private LiveProvider createLiveProvider(final List<Long> liveRevisions) {
    return new LiveProvider(null, myLocation, liveRevisions.get(liveRevisions.size() - 1), new MockSvnLogLoader(liveRevisions), ROOT);
  }

  private static void checkBounds(final Pair<Long, Long> bounds, final long startRevision, final int step) {
    assert (bounds.first - startRevision) % step == 0;
    assert (bounds.second - startRevision) % step == 0;
  }

  private static final class MockCachedProvider extends CachedProvider {
    private MockCachedProvider(final Iterator<ChangesBunch> iterator, final Origin origin) {
      super(iterator, origin);
    }

    @Override
    public void doCacheUpdate(final List<List<Fragment>> fragments) {
      assert false;
    }
  }

  private Iterator<ChangesBunch> createCommittedIterator(final int bunchSize, final List<Long> revisions) {
    final List<ChangesBunch> list = new ArrayList<>();
    for (int i = revisions.size() - 1; i >= 0; i -= bunchSize) {
      final int j = (bunchSize > i) ? -1 : (i - bunchSize);
      final List<CommittedChangeList> subList = new ArrayList<>();
      for (int k = i; k > j; -- k) {
        subList.add(createList(revisions.get(k)));
      }
      list.add(new ChangesBunch(subList, (j != -1)));
      if (j == -1) {
        break;
      }
    }
    return list.iterator();
  }

  private void performTest(final long startRevision, final int step, final Pair<Long, Long> committedBounds,
                           final List<Pair<Long, Long>> internalBounds, final long endRevision) throws Exception {
    assert ((endRevision - startRevision) % step) == 0;
    if (committedBounds != null) {
      checkBounds(committedBounds, startRevision, step);
    }
    for (Pair<Long, Long> bound : internalBounds) {
      checkBounds(bound, startRevision, step);
    }

    final List<Long> liveRevisions = new ArrayList<>();
    final List<Long> committedRevisions = new ArrayList<>();
    for (long i = startRevision; i <= endRevision; i += step) {
      liveRevisions.add(i);
      if (committedBounds != null) {
        if ((committedBounds.first <= i) && (committedBounds.second >= i)) {
          committedRevisions.add(i);
        }
      }
    }

    // each pair corresponds to interval
    final List<Long> internalRevisions = new ArrayList<>();
    LoadedRevisionsCache.Bunch bindTo = null;
    for (int i = 0; i < internalBounds.size(); i++) {
      final Pair<Long, Long> bound = internalBounds.get(i);
      final boolean consistent = (i != 0) && (internalBounds.get(i - 1).second + step == bound.first);
      final List<Long> revisions = new ArrayList<>();
      for (long j = bound.first; j <= bound.second; j += step) {
        revisions.add(j);
        internalRevisions.add(j);
      }
      bindTo = putToInternalCache(revisions, consistent, bindTo);
    }

    assert Collections.disjoint(committedRevisions, internalRevisions);

    final LiveProvider liveProvider = createLiveProvider(liveRevisions);
    final CachedProvider committedProvider = committedRevisions.isEmpty() ? null :
                                             new MockCachedProvider(createCommittedIterator(PAGE, committedRevisions), Origin.VISUAL);
    final CachedProvider internalProvider = internalRevisions.isEmpty() ? null :
                                            new MockCachedProvider(myInternalManager.iterator(myLocation.getURL()), Origin.INTERNAL);

    final BunchFactory factory = new BunchFactory(internalProvider, committedProvider, liveProvider);
    Ref<Boolean> myYoungestRead = new Ref<>(Boolean.FALSE);
    long i = endRevision;
    int pageStep = step * PAGE;
    for (; i >= startRevision; i -= pageStep) {
      assert (! Boolean.TRUE.equals(myYoungestRead.get()));
      final List<Fragment> fragments = factory.goBack(PAGE, myYoungestRead);
      checkFragments(i, internalRevisions, committedRevisions, fragments, step);
    }
    // otherwise end of live stream is not jet detected (additional request is required)
    assert (! (i + step < startRevision) ^ Boolean.TRUE.equals(myYoungestRead.get()));
  }

  private static void checkFragments(final long earlyRevision, final List<Long> internally, final List<Long> committed,
                                     final List<Fragment> fragments, final int step) {
    final List<Long> expectedRevisions = new ArrayList<>();
    int pageStep = PAGE * step;
    for (long i = earlyRevision; i > (earlyRevision - pageStep); i -= step) {
      expectedRevisions.add(i);
    }

    for (Fragment fragment : fragments) {
      final Origin currentOrigin = fragment.getOrigin();
      for (CommittedChangeList list : fragment.getList()) {
        assert ! expectedRevisions.isEmpty();
        final long expected = expectedRevisions.remove(0);
        assert expected == list.getNumber();
        assert (Origin.INTERNAL.equals(currentOrigin) && (internally.contains(expected))) ||
               (Origin.VISUAL.equals(currentOrigin) && (committed.contains(expected))) ||
               (Origin.LIVE.equals(currentOrigin) && (! internally.contains(expected)) && (! committed.contains(expected)));
      }
    }
  }

  private LoadedRevisionsCache.Bunch putToInternalCache(final List<Long> revisions, final boolean consistent, final LoadedRevisionsCache.Bunch bindTo) {
    return myInternalManager.put(reverse(map(revisions, this::createList)), consistent, bindTo);
  }

  public void testJustLiveProvider() throws Exception {
    performTest(11, 2, null, emptyList(), 121);
  }

  public void testLiveAndSimpleInternalProvider() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, null, singletonList(new Pair<>(109L, 117L)), 121 + i);
    }
  }

  public void testLiveAndSimpleCommittedProvider() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, new Pair<>(19L, 117L), emptyList(), 121 + i);
    }
  }

  public void testLiveAndTwoInternalsProvider() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, null, asList(new Pair<>(101L, 111L), new Pair<>(113L, 117L)), 121 + i);
    }
  }

  public void testCommittedAndSeveralInternalsProvider() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, new Pair<>(11L, 17L), asList(new Pair<>(19L, 23L), new Pair<>(25L, 37L + i)), 37 + i);
    }
  }

  public void testAllThreeProviders() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, new Pair<>(11L, 17L), asList(new Pair<>(23L, 37L), new Pair<>(45L, 57L)), 87 + i);
    }
  }

  public void testShift() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, new Pair<>(11L, 15L), asList(new Pair<>(17L, 25L), new Pair<>(27L, 35L)), 37 + i);
    }
  }

  public void testShortLive() throws Exception {
    performTest(11, 2, null, emptyList(), 13);
  }

  public void testShortInternal() throws Exception {
    performTest(11, 2, null, singletonList(new Pair<>(11L, 15L)), 15);
  }

  public void testShortCommitted() throws Exception {
    performTest(11, 2, new Pair<>(11L, 15L), emptyList(), 15);
  }

  public void testThreeByOne() throws Exception {
    performTest(11, 2, new Pair<>(11L, 11L), singletonList(new Pair<>(13L, 13L)), 15);
  }
}
