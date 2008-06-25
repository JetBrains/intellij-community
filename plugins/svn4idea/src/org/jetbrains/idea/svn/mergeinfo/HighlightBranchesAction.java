package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListDecorator;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListsListener;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.LabeledComboBoxAction;
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
import java.util.List;
import java.util.Map;

public class HighlightBranchesAction extends LabeledComboBoxAction implements SelectRootListener {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.mergeinfo.HighlightBranchesAction");
  private final SvnVcs myVcs;
  private final DecoratorManager myManager;
  private WCInfoWithBranches mySelectedRoot;
  public static final DefaultComboBoxModel EMPTY = new DefaultComboBoxModel();
  private final SvnMergeInfoPersistentCache myMergeInfoCache;
  private MyDecorator myDecorator;
  private final String ourIntegratedText = SvnBundle.message("committed.changes.merge.status.integrated.text");
  private final static SimpleTextAttributes ourIntegratedAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.GREEN);
  private final static SimpleTextAttributes ourRefreshAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.GRAY);
  // used ONLY when refresh is triggered
  private final Map<Pair<String, String>, Map<Long, SvnMergeInfoPersistentCache.MergeCheckResult>> myCachedMap;

  public HighlightBranchesAction(final Project project, final DecoratorManager manager) {
    super(SvnBundle.message("committed.changes.action.merge.highlighting.select.branch"));
    myVcs = SvnVcs.getInstance(project);
    myManager = manager;

    myCachedMap = new HashMap<Pair<String, String>, Map<Long, SvnMergeInfoPersistentCache.MergeCheckResult>>();
    myMergeInfoCache = SvnMergeInfoPersistentCache.getInstance(project);

    myDecorator = new MyDecorator();
    manager.install(myDecorator);
  }

  public void deactivate() {
    myManager.remove(myDecorator);
  }

  protected void selectionChanged(final Object selection) {
    myManager.repaintTree();
  }

  public void refresh() {
    final WCInfoWithBranches.Branch branch = (WCInfoWithBranches.Branch) getSelected();
    if ((! isEnabled()) || (branch == null) || (mySelectedRoot == null)) {
      return;
    }
    // on awt thread
    myCachedMap.put(new Pair<String, String>(mySelectedRoot.getPath(), branch.getUrl()), myMergeInfoCache.getCachedState(mySelectedRoot, branch));
    myMergeInfoCache.clear(mySelectedRoot, branch);

    // run refresher
    myManager.reportLoadedLists(new MyRefresher(mySelectedRoot, branch));
    myManager.repaintTree();
  }

  private Map<Long, SvnMergeInfoPersistentCache.MergeCheckResult> getCurrentCache() {
    return myCachedMap.get(new Pair<String, String>(mySelectedRoot.getPath(), ((WCInfoWithBranches.Branch) getSelected()).getUrl()));
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

  private class MyDecorator implements CommittedChangeListDecorator {
    @Nullable
    public List<Pair<String,SimpleTextAttributes>> decorate(final CommittedChangeList list) {
      if ((! isEnabled()) || (getSelected() == null) || (mySelectedRoot == null)) {
        return null;
      }

      final List<Pair<String,SimpleTextAttributes>> decoration = new ArrayList<Pair<String, SimpleTextAttributes>>();

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
        final SvnMergeInfoPersistentCache.MergeCheckResult state = myMergeInfoCache.getState(mySelectedRoot, (SvnChangeList) list, (WCInfoWithBranches.Branch) getSelected());

        final String integrationText = getBeforeText(state);
        if (integrationText != null) {
          decoration.add(new Pair<String, SimpleTextAttributes>(integrationText, ourIntegratedAttributes));
        }
        return decoration;
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

  public void selectionChanged(final WCInfoWithBranches wcInfoWithBranches) {
    final boolean valueChanges = (wcInfoWithBranches != null) && (!wcInfoWithBranches.equals(mySelectedRoot));
    mySelectedRoot = wcInfoWithBranches;

    if (isEnabled()) {
      enableSelf(mySelectedRoot != null);
      if (valueChanges) {
        setModel(createModel());
        myManager.repaintTree();
      }
    }
  }

  public void force(final WCInfoWithBranches info) {
    mySelectedRoot = info;

    if (info == null) {
      setModel(EMPTY);
    } else {

      final WCInfoWithBranches.Branch selected = (WCInfoWithBranches.Branch) getSelected();
      final ComboBoxModel model = createModel();
      setModel(model);

      if (selected != null) {
        boolean selectedSet = false;
        for (int i = 0; i < model.getSize(); i++) {
          final WCInfoWithBranches.Branch element = (WCInfoWithBranches.Branch) model.getElementAt(i);
          if (selected.equals(element)) {
            model.setSelectedItem(element);
            selectedSet = true;
          }
        }
        if (! selectedSet) {
          model.setSelectedItem(model.getElementAt(0));
        }
      } else {
        if (model.getSize() > 0) {
          model.setSelectedItem(model.getElementAt(0));
        }
      }
    }

    if (isEnabled()) {
      enableSelf(mySelectedRoot != null);
      myManager.repaintTree();
    }
  }

  public boolean enablesRefresh() {
    return (mySelectedRoot != null) && (getSelected() != null) && (getCurrentCache() == null);
  }

  protected ComboBoxModel createModel() {
    if (mySelectedRoot == null) {
      return EMPTY;
    }

    return new DefaultComboBoxModel(mySelectedRoot.getBranches().toArray());
  }

  public void enable(final boolean value) {
    enableSelf(value);
    myManager.repaintTree();
  }
}
