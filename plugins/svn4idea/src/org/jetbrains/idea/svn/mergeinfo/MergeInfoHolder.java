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
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.SvnChangeList;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// todo incomplete' to be used as controller
public class MergeInfoHolder {
  private final SvnVcs myVcs;
  private final DecoratorManager myManager;
  private WCInfoWithBranches mySelectedRoot;
  // todo needed?
  public static final DefaultComboBoxModel EMPTY = new DefaultComboBoxModel();
  private final SvnMergeInfoPersistentCache myMergeInfoCache;
  private MyDecorator myDecorator;

  private final static String ourIntegratedText = SvnBundle.message("committed.changes.merge.status.integrated.text");
  private final static SimpleTextAttributes ourIntegratedAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.GREEN);
  private final static SimpleTextAttributes ourRefreshAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.GRAY);
  // used ONLY when refresh is triggered
  private final Map<Pair<String, String>, Map<Long, SvnMergeInfoPersistentCache.MergeCheckResult>> myCachedMap;

  private Getter<WCInfoWithBranches> myRootGetter;
  private Getter<WCInfoWithBranches.Branch> myBranchGetter;
  private Getter<String> myWcPathGetter;

  public MergeInfoHolder(final Project project, final DecoratorManager manager, final Getter<WCInfoWithBranches> rootGetter,
                         final Getter<WCInfoWithBranches.Branch> branchGetter,
                         final Getter<String> wcPathGetter) {
    myRootGetter = rootGetter;
    myBranchGetter = branchGetter;
    myWcPathGetter = wcPathGetter;

    myManager = manager;
    myVcs = SvnVcs.getInstance(project);
    myDecorator = new MyDecorator();
    myMergeInfoCache = SvnMergeInfoPersistentCache.getInstance(project);

    myCachedMap = new HashMap<Pair<String, String>, Map<Long, SvnMergeInfoPersistentCache.MergeCheckResult>>();
  }

  private Map<Long, SvnMergeInfoPersistentCache.MergeCheckResult> getCurrentCache() {
    //return myCachedMap.get(new Pair<String, String>(mySelectedRoot.getPath(), ((WCInfoWithBranches.Branch) getSelected()).getUrl()));
    return null;
  }

  private class MyRefresher implements CommittedChangeListsListener {
    private final WCInfoWithBranches myRefreshedRoot;
    private final WCInfoWithBranches.Branch myRefreshedBranch;

    private MyRefresher(final WCInfoWithBranches refreshedRoot, final WCInfoWithBranches.Branch refreshedBranch) {
      myRefreshedRoot = refreshedRoot;
      myRefreshedBranch = refreshedBranch;
    }

    public void onBeforeStartReport() {
    }

    public boolean report(final CommittedChangeList list) {
      // prepare state. must be in non awt thread
      myMergeInfoCache.getState(myRefreshedRoot, (SvnChangeList) list, myRefreshedBranch);
      // todo make batches - by 10
      final long number = list.getNumber();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          final Map<Long, SvnMergeInfoPersistentCache.MergeCheckResult> map = myCachedMap.get(
              new Pair<String, String>(myRefreshedRoot.getPath(), myRefreshedBranch.getUrl()));
          if (map != null) {
            map.remove(number);
          }
          myManager.repaintTree();
        }
      });
      return true;
    }

    public void onAfterEndReport() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myCachedMap.remove(new Pair<String, String>(myRefreshedRoot.getPath(), myRefreshedBranch.getUrl()));
          myManager.repaintTree();
        }
      });
    }
  }

  private final static Color MERGED = new Color(0, 100, 0);
  private final static Color DIRTY_MERGED = new Color(50, 100, 50);
  private final static Color DIRTY = new Color(50, 50, 50);

  private class MyDecorator implements CommittedChangeListDecorator {
    @Nullable
    public java.util.List<Pair<String,SimpleTextAttributes>> decorate(final CommittedChangeList list) {
      /*if ((! isEnabled()) || (getSelected() == null) || (mySelectedRoot == null)) {
        return null;
      }*/

      final java.util.List<Pair<String,SimpleTextAttributes>> decoration = new ArrayList<Pair<String, SimpleTextAttributes>>();

        final Map<Long, SvnMergeInfoPersistentCache.MergeCheckResult> map = getCurrentCache();
        if (map != null) {
          final SvnMergeInfoPersistentCache.MergeCheckResult result = map.get(list.getNumber());
          if (result != null) {
            String beforeText = getBeforeText(result);
            if (beforeText != null) {
              decoration.add(new Pair<String, SimpleTextAttributes>(beforeText, ourIntegratedAttributes));
            }
          }
          decoration.add(new Pair<String, SimpleTextAttributes>(SvnBundle.message("committed.changes.merge.status.refreshing.text"), ourRefreshAttributes));
          return decoration;
        }

      if ((myVcs != null) && (myVcs.getName().equals(list.getVcs().getName()))) {
        /*final SvnMergeInfoPersistentCache.MergeCheckResult state = myMergeInfoCache.getState(mySelectedRoot, (SvnChangeList) list, (WCInfoWithBranches.Branch) getSelected());

        final String integrationText = getBeforeText(state);
        if (integrationText != null) {
          decoration.add(new Pair<String, SimpleTextAttributes>(integrationText, ourIntegratedAttributes));
        }
        return decoration;*/
      }
      return null;
    }

    @Nullable
    private String getBeforeText(final SvnMergeInfoPersistentCache.MergeCheckResult result) {
      if (SvnMergeInfoPersistentCache.MergeCheckResult.MERGED.equals(result)) {
        return ourIntegratedText;
      }
      return null;
    }
  }
}
