package org.jetbrains.idea.svn16;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.ChangesBunch;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.history.*;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.*;

public class SvnCachingRevisionsTest extends CodeInsightFixtureTestCase {
  private SvnRepositoryLocation myLocation;
  private LoadedRevisionsCache myInternalManager;
  private final static String URL = "file:///C:/repo/trunk";
  private final static SVNURL ROOT = SvnUtil.parseUrl("file:///C:/repo");
  private final static String AUTHOR = "author";
  private final static int PAGE = 5;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLocation = new SvnRepositoryLocation(URL);
    myInternalManager = LoadedRevisionsCache.getInstance(myFixture.getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    FileUtil.delete(SvnApplicationSettings.getLoadedRevisionsDir(myFixture.getProject()));
    super.tearDown();
  }

  private SvnChangeList createList(final long revision) {
    LogEntry entry =
      new LogEntry.Builder().setRevision(revision).setAuthor(AUTHOR).setDate(new Date(System.currentTimeMillis())).setMessage("").build();
    return new SvnChangeList(null, myLocation, entry, ROOT.toDecodedString());
  }

  private class MockSvnLogLoader implements SvnLogLoader {
    private final List<Long> myRevisions;

    private MockSvnLogLoader(final List<Long> revisions) {
      myRevisions = revisions;
    }

    @Override
    public List<CommittedChangeList> loadInterval(final SVNRevision fromIncluding, final SVNRevision toIncluding, final int maxCount,
                                                  final boolean includingYoungest, final boolean includeOldest)
      throws VcsException {
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

  private void checkBounds(final Pair<Long, Long> bounds, final long startRevision, final int step) {
    assert (bounds.first - startRevision) % step == 0;
    assert (bounds.second - startRevision) % step == 0;
  }

  private class MockCachedProvider extends CachedProvider {
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
    for (; i >= startRevision; i -= step * PAGE) {
      assert (! Boolean.TRUE.equals(myYoungestRead.get()));
      final List<Fragment> fragments = factory.goBack(PAGE, myYoungestRead);
      debugFragments(i, fragments);
      checkFragments(i, internalRevisions, committedRevisions, fragments, step);
    }
    // otherwise end of live stream is not jet detected (additional request is required)
    assert (! (i + step < startRevision) ^ Boolean.TRUE.equals(myYoungestRead.get()));
  }

  private void checkFragments(final long earlyRevision, final List<Long> internally, final List<Long> committed,
                              final List<Fragment> fragments, final int step) {
    final List<Long> expectedRevisions = new ArrayList<>();
    for (long i = earlyRevision; i > (earlyRevision - PAGE * step); i -= step) {
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

  private void debugFragments(final long earlyRevision, final List<Fragment> fragments) {
    //System.out.println("Loaded for start revision: " + earlyRevision);
    //for (Fragment fragment : fragments) {
    //  System.out.println(fragment.getOrigin().toString() + " from: " + fragment.getList().get(0).getNumber() +
    //    " to: " + fragment.getList().get(fragment.getList().size() - 1).getNumber());
    //}
    //System.out.println();
  }

  private List<CommittedChangeList> revisionsToLists(final List<Long> revisions) {
    final List<CommittedChangeList> lists = new ArrayList<>();
    for (Long revision : revisions) {
      lists.add(createList(revision));
    }
    return lists;
  }

  private LoadedRevisionsCache.Bunch putToInternalCache(final List<Long> revisions, final boolean consistent, final LoadedRevisionsCache.Bunch bindTo) {
    final List<CommittedChangeList> lists = revisionsToLists(revisions);
    Collections.reverse(lists);
    return myInternalManager.put(lists, consistent, bindTo);
  }

  public void testJustLiveProvider() throws Exception {
    performTest(11, 2, null, Collections.<Pair<Long, Long>>emptyList(), 121);
  }

  public void testLiveAndSimpleInternalProvider() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, null, Collections.singletonList(new Pair<>(109L, 117L)), 121 + i);
    }
  }

  public void testLiveAndSimpleCommittedProvider() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, new Pair<>(19L, 117L), Collections.<Pair<Long, Long>>emptyList(), 121 + i);
    }
  }

  public void testLiveAndTwoInternalsProvider() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, null, Arrays.asList(new Pair<>(101L, 111L), new Pair<>(113L, 117L)), 121 + i);
    }
  }

  public void testCommittedAndSeveralInternalsProvider() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, new Pair<>(11L, 17L), Arrays.asList(new Pair<>(19L, 23L), new Pair<>(25L, 37L + i)), 37 + i);
    }
  }

  public void testAllThreeProviders() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, new Pair<>(11L, 17L), Arrays.asList(new Pair<>(23L, 37L), new Pair<>(45L, 57L)), 87 + i);
    }
  }

  public void testShift() throws Exception {
    for (int i = 0; i < 2 * PAGE; i+=2) {
      performTest(11, 2, new Pair<>(11L, 15L), Arrays.asList(new Pair<>(17L, 25L), new Pair<>(27L, 35L)), 37 + i);
    }
  }

  public void testShortLive() throws Exception {
    performTest(11, 2, null, Collections.<Pair<Long, Long>>emptyList(), 13);
  }

  public void testShortInternal() throws Exception {
    performTest(11, 2, null, Collections.singletonList(new Pair<>(11L, 15L)), 15);
  }
  
  public void testShortCommitted() throws Exception {
    performTest(11, 2, new Pair<>(11L, 15L), Collections.<Pair<Long, Long>>emptyList(), 15);
  }

  public void testThreeByOne() throws Exception {
    performTest(11, 2, new Pair<>(11L, 11L), Collections.singletonList(new Pair<>(13L, 13L)), 15);
  }
}
