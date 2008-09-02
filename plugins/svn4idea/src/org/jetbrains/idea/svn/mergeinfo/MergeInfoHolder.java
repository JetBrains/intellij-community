package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListsListener;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.dialogs.WCPaths;
import org.jetbrains.idea.svn.history.SvnChangeList;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MergeInfoHolder {
  private final DecoratorManager myManager;
  private final SvnMergeInfoCache myMergeInfoCache;

  private final static String ourIntegratedText = SvnBundle.message("committed.changes.merge.status.integrated.text");
  private final static String ourNotIntegratedText = SvnBundle.message("committed.changes.merge.status.not.integrated.text");
  private final static SimpleTextAttributes ourNotIntegratedAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.RED);
  private final static SimpleTextAttributes ourIntegratedAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.GREEN);
  private final static SimpleTextAttributes ourRefreshAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.GRAY);
  
  // used ONLY when refresh is triggered
  private final Map<Pair<String, String>, Map<Long, SvnMergeInfoCache.MergeCheckResult>> myCachedMap;

  private Getter<WCPaths> myRootGetter;
  private Getter<WCInfoWithBranches.Branch> myBranchGetter;
  private Getter<String> myWcPathGetter;
  private Getter<Boolean> myEnabledHolder;
  private MyDecorator myDecorator;

  public MergeInfoHolder(final Project project, final DecoratorManager manager, final Getter<WCPaths> rootGetter,
                         final Getter<WCInfoWithBranches.Branch> branchGetter,
                         final Getter<String> wcPathGetter, Getter<Boolean> enabledHolder) {
    myRootGetter = rootGetter;
    myBranchGetter = branchGetter;
    myWcPathGetter = wcPathGetter;
    myEnabledHolder = enabledHolder;
    myManager = manager;
    myMergeInfoCache = SvnMergeInfoCache.getInstance(project);
    myCachedMap = new HashMap<Pair<String, String>, Map<Long, SvnMergeInfoCache.MergeCheckResult>>();

    myDecorator = new MyDecorator();
  }

  private Map<Long, SvnMergeInfoCache.MergeCheckResult> getCurrentCache() {
    return myCachedMap.get(createKey(myRootGetter.get(), myBranchGetter.get()));
  }

  private boolean enabledAndGettersFilled(final boolean ignoreEnabled) {
    if ((! ignoreEnabled) && (! Boolean.TRUE.equals(myEnabledHolder.get()))) {
      return false;
    }
    return (myRootGetter.get() != null) && (myBranchGetter.get() != null) && (myWcPathGetter.get() != null);
  }

  public boolean refreshEnabled(final boolean ignoreEnabled) {
    return enabledAndGettersFilled(ignoreEnabled) && (getCurrentCache() == null);
  }

  private static Pair<String, String> createKey(final WCPaths root, final WCInfoWithBranches.Branch branch) {
    return new Pair<String, String>(root.getPath(), branch.getUrl());
  }

  @Nullable
  public void refresh(final boolean ignoreEnabled) {
    final CommittedChangeListsListener refresher = createRefresher(ignoreEnabled);
    if (refresher != null) {
      myManager.reportLoadedLists(new MyRefresher());
    }
    myManager.repaintTree();
  }

  @Nullable
  public CommittedChangeListsListener createRefresher(final boolean ignoreEnabled) {
    if (refreshEnabled(ignoreEnabled)) {
      // on awt thread
      final Map<Long, SvnMergeInfoCache.MergeCheckResult> map =
          myMergeInfoCache.getCachedState(myRootGetter.get(), myBranchGetter.get());
      myCachedMap.put(createKey(myRootGetter.get(), myBranchGetter.get()),
                      (map == null) ? new HashMap<Long, SvnMergeInfoCache.MergeCheckResult>() : map);
      myMergeInfoCache.clear(myRootGetter.get(), myBranchGetter.get());

      return new MyRefresher();
    }
    return null;
  }

  private class MyRefresher implements CommittedChangeListsListener {
    private final WCPaths myRefreshedRoot;
    private final WCInfoWithBranches.Branch myRefreshedBranch;
    private final String myBranchPath;

    private MyRefresher() {
      myRefreshedRoot = myRootGetter.get();
      myRefreshedBranch = myBranchGetter.get();
      myBranchPath = myWcPathGetter.get();
    }

    public void onBeforeStartReport() {
    }

    public boolean report(final CommittedChangeList list) {
      if (list instanceof SvnChangeList) {
        final SvnChangeList svnList = (SvnChangeList) list;
        final String wcPath = svnList.getWcPath() + File.separator;
        // todo check if this needed
        /*if (! myRefreshedRoot.getPath().equals(wcPath)) {
          return true;
        } */

        // prepare state. must be in non awt thread
        final SvnMergeInfoCache.MergeCheckResult state = myMergeInfoCache.getState(myRefreshedRoot, (SvnChangeList)list, myRefreshedBranch,
                                                                                   myBranchPath);
        // todo make batches - by 10
        final long number = list.getNumber();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final Map<Long, SvnMergeInfoCache.MergeCheckResult> map = myCachedMap.get(createKey(myRefreshedRoot, myRefreshedBranch));
            if (map != null) {
              map.put(number, state);
            }
            myManager.repaintTree();
          }
        });
      }
      return true;
    }

    public void onAfterEndReport() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myCachedMap.remove(createKey(myRefreshedRoot, myRefreshedBranch));
          myManager.repaintTree();
        }
      });
    }
  }

  public static enum ListMergeStatus {
    MERGED(IconLoader.getIcon("/icons/Integrated.png")),
    NOT_MERGED(IconLoader.getIcon("/icons/Notintegrated.png")),
    //ALIEN(IconLoader.getIcon("/icons/OnDefault.png")),
    ALIEN(null),
    REFRESHING(IconLoader.getIcon("/icons/IntegrationStatusUnknown.png"));

    private final Icon myIcon;

    private ListMergeStatus(final Icon icon) {
      myIcon = icon;
    }

    public Icon getIcon() {
      return myIcon;
    }
  }

  public static interface ListChecker {
    ListMergeStatus check(final CommittedChangeList list, final boolean ignoreEnabled);
  }

  class MyDecorator implements ListChecker {
    private ListMergeStatus convert(SvnMergeInfoCache.MergeCheckResult result, final boolean refreshing) {
      if (result != null) {
        if (SvnMergeInfoCache.MergeCheckResult.MERGED.equals(result)) {
          return ListMergeStatus.MERGED;
        } else {
          return ListMergeStatus.NOT_MERGED;
        }
      }
      if (refreshing) {
        return ListMergeStatus.REFRESHING;
      }
      return ListMergeStatus.ALIEN;
    }

    public ListMergeStatus check(final CommittedChangeList list, final boolean ignoreEnabled) {
      if (! enabledAndGettersFilled(ignoreEnabled)) {
        return ListMergeStatus.ALIEN;
      }

      if (! (list instanceof SvnChangeList)) {
        return ListMergeStatus.ALIEN;
      }

      final Map<Long, SvnMergeInfoCache.MergeCheckResult> map = getCurrentCache();
      if (map != null) {
        final SvnMergeInfoCache.MergeCheckResult result = map.get(list.getNumber());
        return convert(result, true);
      } else {
        final Map<Long, SvnMergeInfoCache.MergeCheckResult> state =
            myMergeInfoCache.getCachedState(myRootGetter.get(), myBranchGetter.get());
        if (state == null) {
          refresh(ignoreEnabled);
          return ListMergeStatus.REFRESHING;
        } else {
          return convert(state.get(list.getNumber()), false);
        }
      }
    }
  }

  public ListChecker getDecorator() {
    return myDecorator;
  }
}
