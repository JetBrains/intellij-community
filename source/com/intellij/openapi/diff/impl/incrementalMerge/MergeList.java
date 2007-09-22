package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.diff.impl.processing.DiffPolicy;
import com.intellij.openapi.diff.impl.util.ContextLogger;
import com.intellij.openapi.diff.impl.util.GutterActionRenderer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.containers.SequenceIterator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MergeList implements ChangeList.Parent, UserDataHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.MergeList");
  private final Project myProject;
  private final ChangeList[] myChanges = new ChangeList[2];
  private final UserDataHolderBase myDataHolder = new UserDataHolderBase();
  public static final FragmentSide BRANCH_SIDE = FragmentSide.SIDE2;
  public static final FragmentSide BASE_SIDE = FragmentSide.SIDE1;
  @NonNls public static final String MERGE_LIST = "mergeList";

  private MergeList(Project project, Document left, Document base, Document right) {
    myProject = project;
    myChanges[0] = new ChangeList(base, left, this);
    myChanges[1] = new ChangeList(base, right, this);
  }

  public static MergeList create(Project project, Document left, Document base, Document right) {
    MergeList mergeList = new MergeList(project, left, base, right);
    String leftText = left.getText();
    String baseText = base.getText();
    String rightText = right.getText();
    @NonNls final Object[] data = new Object[]{
      "Left\n" + leftText,
      "\nBase\n" + baseText,
      "\nRight\n" + rightText
    };
    ContextLogger logger = new ContextLogger(LOG, new ContextLogger.SimpleContext(data));
    List<MergeBuilder.MergeFragment> fragmentList = processText(leftText, baseText, rightText, logger);

    ArrayList<Change> leftChanges = new ArrayList<Change>();
    ArrayList<Change> rightChanges = new ArrayList<Change>();
    for (MergeBuilder.MergeFragment mergeFragment : fragmentList) {
      final TextRange[] ranges = mergeFragment.getRanges();
      logger.assertTrue(ranges[1] != null);
      if (ranges[0] == null) {
        if (ranges[2] == null) {
          LOG.assertTrue(false, "Left Text: " + leftText + "\n" + "Right Text: " + rightText + "\nBase Text: " + baseText);
        }
        rightChanges.add(SimpleChange.fromRanges(ranges[1], ranges[2], mergeList.myChanges[1]));
      }
      else if (ranges[2] == null) {
        if (ranges[0] == null) {
          LOG.assertTrue(false, "Left Text: " + leftText + "\n" + "Right Text: " + rightText + "\nBase Text: " + baseText);
        }
        leftChanges.add(SimpleChange.fromRanges(ranges[1], ranges[0], mergeList.myChanges[0]));
      }
      else {
        Change[] changes = MergeConflict.createChanges(ranges[0], ranges[1], ranges[2], mergeList);
        leftChanges.add(changes[0]);
        rightChanges.add(changes[1]);
      }
    }
    mergeList.myChanges[0].setChanges(leftChanges);
    mergeList.myChanges[1].setChanges(rightChanges);
    return mergeList;
  }

  private static List<MergeBuilder.MergeFragment> processText(String leftText,
                                                              String baseText,
                                                              String rightText,
                                                              ContextLogger logger) {
    DiffFragment[] leftFragments = DiffPolicy.DEFAULT_LINES.buildFragments(baseText, leftText);
    DiffFragment[] rightFragments = DiffPolicy.DEFAULT_LINES.buildFragments(baseText, rightText);
    int[] leftOffsets = new int[]{0, 0};
    int[] rightOffsets = new int[]{0, 0};
    int leftIndex = 0;
    int rightIndex = 0;
    MergeBuilder builder = new MergeBuilder(logger);
    while (leftIndex < leftFragments.length || rightIndex < rightFragments.length) {
      FragmentSide side;
      TextRange[] equalRanges = new TextRange[2];
      if (leftOffsets[0] < rightOffsets[0] && leftIndex < leftFragments.length) {
        side = FragmentSide.SIDE1;
        getEqualRanges(leftFragments[leftIndex], leftOffsets, equalRanges);
        leftIndex++;
      } else if (rightIndex < rightFragments.length) {
        side = FragmentSide.SIDE2;
        getEqualRanges(rightFragments[rightIndex], rightOffsets, equalRanges);
        rightIndex++;
      } else break;
      if (equalRanges[0] != null && equalRanges[1] != null) builder.add(equalRanges[0], equalRanges[1], side);
      else logger.assertTrue(equalRanges[0] == null && equalRanges[1] == null);
    }
    List<MergeBuilder.MergeFragment> fragmentList = builder.finish(leftText.length(), baseText.length(),
                                                                   rightText.length());
    return fragmentList;
  }

  private static void getEqualRanges(DiffFragment fragment, int[] leftOffsets, TextRange[] equalRanges) {
    int baseLength = getTextLength(fragment.getText1());
    int versionLength = getTextLength(fragment.getText2());
    if (fragment.isEqual()) {
      equalRanges[0] = new TextRange(leftOffsets[0], leftOffsets[0] + baseLength);
      equalRanges[1] = new TextRange(leftOffsets[1], leftOffsets[1] + versionLength);
    } else {
      equalRanges[0] = null;
      equalRanges[1] = null;
    }
    leftOffsets[0] += baseLength;
    leftOffsets[1] += versionLength;
  }

  private static int getTextLength(String text1) {
    return text1 != null ? text1.length() : 0;
  }

  public static MergeList create(DiffRequest data) {
    DiffContent[] contents = data.getContents();
    return create(data.getProject(), contents[0].getDocument(), contents[1].getDocument(), contents[2].getDocument());
  }

  public void setMarkups(Editor left, Editor base, Editor right) {
    myChanges[0].setMarkup(base, left);
    myChanges[1].setMarkup(base, right);
    addActions(FragmentSide.SIDE1);
    addActions(FragmentSide.SIDE2);
  }

  public Iterator<Change> getAllChanges() {
    return SequenceIterator.create(myChanges[0].getChanges().iterator(),
                                   FilteringIterator.create(myChanges[1].getChanges().iterator(), NOT_CONFLICTS));
  }

  public void addListener(ChangeList.Listener listener) {
    for (ChangeList changeList : myChanges) {
      changeList.addListener(listener);
    }
  }

  public void removeListener(ChangeList.Listener listener) {
    for (ChangeList changeList : myChanges) {
      changeList.removeListener(listener);
    }
  }

  private void addActions(final FragmentSide side) {
    ChangeList changeList = myChanges[side.getIndex()];
    final FragmentSide originalSide = BRANCH_SIDE;
    for (int i = 0; i < changeList.getCount(); i++) {
      final Change change = changeList.getChange(i);
      if (!change.canHasActions(originalSide)) continue;
      AnAction applyAction = new AnAction(DiffBundle.message("merge.dialog.apply.change.action.name"), null, GutterActionRenderer.REPLACE_ARROW) {
        public void actionPerformed(AnActionEvent e) {
          apply(change);
        }
      };
      AnAction ignoreAction = new AnAction(DiffBundle.message("merge.dialog.ignore.change.action.name"), null, GutterActionRenderer.REMOVE_CROSS) {
        public void actionPerformed(AnActionEvent e) {
          change.removeFromList();
        }
      };
      change.getChangeSide(originalSide).getHighlighterHolder().setActions(new AnAction[]{applyAction, ignoreAction});
    }
  }

  private static void apply(final Change change) {
    Change.apply(change, BRANCH_SIDE);
  }

  public ChangeList getChanges(final FragmentSide changesSide) {
    return myChanges[changesSide.getIndex()];
  }

  public void removeChanges(Change[] changes) {
    for (int i = 0; i < changes.length; i++) {
      Change change = changes[i];
      myChanges[i].remove(change);
    }
  }

  public Document getBaseDocument() {
    Document document = myChanges[0].getDocument(BASE_SIDE);
    LOG.assertTrue(document == myChanges[1].getDocument(BASE_SIDE));
    return document;
  }

  @Nullable
  public static MergeList fromDataContext(DataContext dataContext) {
    MergeList mergeList = (MergeList)dataContext.getData(MERGE_LIST);
    if (mergeList != null) return mergeList;
    MergePanel2 mergePanel = MergePanel2.fromDataContext(dataContext);
    return mergePanel == null ? null : mergePanel.getMergeList();
  }

  public static final Condition<Change> NOT_CONFLICTS = new Condition<Change>() {
    public boolean value(Change change) {
      return !(change instanceof ConflictChange);
    }
  };

  public Project getProject() {
    return myProject;
  }

  public <T> T getUserData(Key<T> key) {
    return myDataHolder.getUserData(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    myDataHolder.putUserData(key, value);
  }

  public FragmentSide getSideOf(ChangeList source) {
    for (int i = 0; i < myChanges.length; i++) {
      ChangeList changeList = myChanges[i];
      if (changeList == source) return FragmentSide.fromIndex(i);
    }
    return null;
  }

  public void updateMarkup() {
    myChanges[0].updateMarkup();
    myChanges[1].updateMarkup();
  }
}
