package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListDecorator;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListsListener;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

  private Getter<WCInfoWithBranches> myRootGetter;
  private Getter<WCInfoWithBranches.Branch> myBranchGetter;
  private Getter<String> myWcPathGetter;
  private Getter<Boolean> myEnabledHolder;

  public MergeInfoHolder(final Project project, final DecoratorManager manager, final Getter<WCInfoWithBranches> rootGetter,
                         final Getter<WCInfoWithBranches.Branch> branchGetter,
                         final Getter<String> wcPathGetter, Getter<Boolean> enabledHolder) {
    myRootGetter = rootGetter;
    myBranchGetter = branchGetter;
    myWcPathGetter = wcPathGetter;
    myEnabledHolder = enabledHolder;
    myManager = manager;
    myMergeInfoCache = SvnMergeInfoCache.getInstance(project);
    myCachedMap = new HashMap<Pair<String, String>, Map<Long, SvnMergeInfoCache.MergeCheckResult>>();

    myManager.install(new MyDecorator());
  }

  private Map<Long, SvnMergeInfoCache.MergeCheckResult> getCurrentCache() {
    return myCachedMap.get(createKey(myRootGetter.get(), myBranchGetter.get()));
  }

  private boolean enabledAndGettersFilled() {
    return (Boolean.TRUE.equals(myEnabledHolder.get())) && (myRootGetter.get() != null) && (myBranchGetter.get() != null) &&
           (myWcPathGetter.get() != null);
  }

  public boolean refreshEnabled() {
    return enabledAndGettersFilled() && (getCurrentCache() == null);
  }

  private static Pair<String, String> createKey(final WCInfoWithBranches root, final WCInfoWithBranches.Branch branch) {
    return new Pair<String, String>(root.getPath(), branch.getUrl());
  }

  public void refresh() {
    if (refreshEnabled()) {
      // on awt thread
      final Map<Long, SvnMergeInfoCache.MergeCheckResult> map =
          myMergeInfoCache.getCachedState(myRootGetter.get(), myBranchGetter.get());
      myCachedMap.put(createKey(myRootGetter.get(), myBranchGetter.get()),
                      (map == null) ? new HashMap<Long, SvnMergeInfoCache.MergeCheckResult>() : map);
      myMergeInfoCache.clear(myRootGetter.get(), myBranchGetter.get());

      // run refresher
      myManager.reportLoadedLists(new MyRefresher());
      myManager.repaintTree();
    }
  }

  private class MyRefresher implements CommittedChangeListsListener {
    private final WCInfoWithBranches myRefreshedRoot;
    private final WCInfoWithBranches.Branch myRefreshedBranch;

    private MyRefresher() {
      myRefreshedRoot = myRootGetter.get();
      myRefreshedBranch = myBranchGetter.get();
    }

    public void onBeforeStartReport() {
    }

    public boolean report(final CommittedChangeList list) {
      if (list instanceof SvnChangeList) {
        // prepare state. must be in non awt thread
        myMergeInfoCache.getState(myRefreshedRoot, (SvnChangeList) list, myRefreshedBranch);
        // todo make batches - by 10
        final long number = list.getNumber();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final Map<Long, SvnMergeInfoCache.MergeCheckResult> map = myCachedMap.get(createKey(myRefreshedRoot, myRefreshedBranch));
            if (map != null) {
              map.remove(number);
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

  private class MyDecorator implements CommittedChangeListDecorator {
    private void fromCached(final SvnMergeInfoCache.MergeCheckResult result,
                            final List<Pair<String,SimpleTextAttributes>> decoration, final boolean refreshing) {
      if (result != null) {
        if (SvnMergeInfoCache.MergeCheckResult.MERGED.equals(result)) {
          decoration.add(new Pair<String, SimpleTextAttributes>(ourIntegratedText, ourIntegratedAttributes));
        } else {
          decoration.add(new Pair<String, SimpleTextAttributes>(ourNotIntegratedText, ourNotIntegratedAttributes));
        }
      }
      if (refreshing) {
        decoration.add(new Pair<String, SimpleTextAttributes>(
            SvnBundle.message("committed.changes.merge.status.refreshing.text"), ourRefreshAttributes));
      }
    }

    @Nullable
    public List<Pair<String,SimpleTextAttributes>> decorate(final CommittedChangeList list) {
      if (! enabledAndGettersFilled()) {
        return null;
      }

      if (! (list instanceof SvnChangeList)) {
        return null;
      }

      final List<Pair<String,SimpleTextAttributes>> decoration = new ArrayList<Pair<String, SimpleTextAttributes>>();

        final Map<Long, SvnMergeInfoCache.MergeCheckResult> map = getCurrentCache();
        if (map != null) {
          final SvnMergeInfoCache.MergeCheckResult result = map.get(list.getNumber());
          fromCached(result, decoration, true);
        } else {
          final Map<Long, SvnMergeInfoCache.MergeCheckResult> state =
              myMergeInfoCache.getCachedState(myRootGetter.get(), myBranchGetter.get());
          if (state == null) {
            refresh();
          } else {
            fromCached(state.get(list.getNumber()), decoration, false);
          }
        }
      return decoration;
    }
  }
}
