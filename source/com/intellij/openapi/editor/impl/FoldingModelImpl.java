/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 4, 2002
 * Time: 8:27:13 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;

public class FoldingModelImpl implements FoldingModelEx, DocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorFoldingModelImpl");
  private boolean myIsFoldingEnabled;
  private EditorImpl myEditor;
  private FoldRegionsTree myFoldTree;
  private TextAttributes myFoldTextAttributes;
  private boolean myIsBatchFoldingProcessing;
  private boolean myFoldRegionsProcessed;

  private int mySavedCaretX;
  private int mySavedCaretY;
  private int mySavedCaretShift;
  private boolean myCaretPositionSaved;

  public FoldingModelImpl(EditorImpl editor) {
    myEditor = editor;
    myIsFoldingEnabled = true;
    myIsBatchFoldingProcessing = false;
    myFoldTree = new FoldRegionsTree();
    myFoldRegionsProcessed = false;
    refreshSettings();
  }

  public void refreshSettings() {
    myFoldTextAttributes = myEditor.getColorsScheme().getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES);
  }

  public boolean isFoldingEnabled() {
    return myIsFoldingEnabled;
  }

  public boolean isOffsetCollapsed(int offset) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getCollapsedRegionAtOffset(offset) != null;
  }

  public void setFoldingEnabled(boolean isEnabled) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myIsFoldingEnabled = isEnabled;
  }

  public FoldRegion addFoldRegion(int startOffset, int endOffset, String placeholderText) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(placeholderText != null);
    FoldRegion range = new FoldRegionImpl(myEditor, startOffset, endOffset, placeholderText);
    if (isFoldingEnabled()) {
      if (!myIsBatchFoldingProcessing) {
        LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
        return null;
      }

      myFoldRegionsProcessed = true;
      return myFoldTree.addRegion(range) ? range : null;
    }

    return null;
  }

  public void runBatchFoldingOperation(Runnable operation) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean oldBatchFlag = myIsBatchFoldingProcessing;
    if (!oldBatchFlag) {
      mySavedCaretShift = myEditor.visibleLineNumberToYPosition(myEditor.getCaretModel().getVisualPosition().line) - myEditor.getScrollingModel().getVerticalScrollOffset();
    }

    myIsBatchFoldingProcessing = true;
    operation.run();
    if (!oldBatchFlag) {
      if (myFoldRegionsProcessed) {
        notifyBatchFoldingProcessingDone();
        myFoldRegionsProcessed = false;
      }
      myIsBatchFoldingProcessing = false;
    }
  }

  public void flushCaretShift() {
    mySavedCaretShift = -1;
  }

  public FoldRegion[] getAllFoldRegions() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myFoldTree.fetchAllRegions();
  }

  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return myFoldTree.fetchOutermost(offset);
  }

  int getLastTopLevelIndexBefore (int offset) {
    return myFoldTree.getLastTopLevelIndexBefore(offset);
  }

  public FoldRegion getFoldingPlaceholderAt(Point p) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LogicalPosition pos = myEditor.xyToLogicalPosition(p);
    int line = pos.line;

    if (line >= myEditor.getDocument().getLineCount()) return null;
    int offset = myEditor.logicalPositionToOffset(pos);

    return myFoldTree.fetchOutermost(offset);
  }

  public FoldRegion[] getAllFoldRegionsIncludingInvalid() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myFoldTree.fetchAllRegionsIncludingInvalid();
  }

  public void removeFoldRegion(final FoldRegion region) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }

    expandFoldRegion(region);
    myFoldTree.removeRegion(region);
    myFoldRegionsProcessed = true;
  }

  public void expandFoldRegion(FoldRegion region) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (region.isExpanded()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    if (myCaretPositionSaved) {
      int savedOffset = myEditor.logicalPositionToOffset(new LogicalPosition(mySavedCaretY, mySavedCaretX));

      FoldRegion[] allCollapsed = myFoldTree.fetchCollapsedAt(savedOffset);
      if (allCollapsed.length == 1 && allCollapsed[0] == region) {
        LogicalPosition pos = new LogicalPosition(mySavedCaretY, mySavedCaretX);
        myEditor.getCaretModel().moveToLogicalPosition(pos);
      }
    }

    myFoldRegionsProcessed = true;
    ((FoldRegionImpl) region).setExpandedInternal(true);
  }

  public void collapseFoldRegion(FoldRegion region) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!region.isExpanded()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();

    int caretOffset = myEditor.logicalPositionToOffset(caretPosition);

    if (myFoldTree.contains(region, caretOffset)) {
      if (!myCaretPositionSaved) {
        mySavedCaretX = caretPosition.column;
        mySavedCaretY = caretPosition.line;
        myCaretPositionSaved = true;
      }
    }

    int selectionStart = myEditor.getSelectionModel().getSelectionStart();
    int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();

    if (myFoldTree.contains(region, selectionStart-1) || myFoldTree.contains(region, selectionEnd)) myEditor.getSelectionModel().removeSelection();

    myFoldRegionsProcessed = true;
    ((FoldRegionImpl) region).setExpandedInternal(false);
  }

  private void notifyBatchFoldingProcessingDone() {
    myFoldTree.rebuild();

    myEditor.updateCaretCursor();
    myEditor.recalcSizeAndRepaint();
    if (myEditor.getGutterComponentEx().isFoldingOutlineShown()) {
      myEditor.getGutterComponentEx().repaint();
    }

    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
    int caretOffset = myEditor.logicalPositionToOffset(caretPosition);
    int selectionStart = myEditor.getSelectionModel().getSelectionStart();
    int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();

    int column = -1;
    int line = -1;

    FoldRegion collapsed = myFoldTree.fetchOutermost(caretOffset);
    if (myCaretPositionSaved) {
      int savedOffset = myEditor.logicalPositionToOffset(new LogicalPosition(mySavedCaretY, mySavedCaretX));
      FoldRegion collapsedAtSaved = myFoldTree.fetchOutermost(savedOffset);
      column = mySavedCaretX;
      line = collapsedAtSaved != null ? collapsedAtSaved.getDocument().getLineNumber(collapsedAtSaved.getStartOffset()) : mySavedCaretY;
    }

    if (collapsed != null && column == -1) {
      line = collapsed.getDocument().getLineNumber(collapsed.getStartOffset());
      column = myEditor.getCaretModel().getVisualPosition().column;
    }

    boolean oldCaretPositionSaved = myCaretPositionSaved;

    if (column != -1) {
      LogicalPosition log = new LogicalPosition(line, 0);
      VisualPosition vis = myEditor.logicalToVisualPosition(log);
      VisualPosition pos = new VisualPosition(vis.line, column);
      myEditor.getCaretModel().moveToVisualPosition(pos);
    } else {
      myEditor.getCaretModel().moveToLogicalPosition(caretPosition);
    }

    myCaretPositionSaved = oldCaretPositionSaved;

    myEditor.getSelectionModel().setSelection(selectionStart, selectionEnd);

    if (mySavedCaretShift > 0) {
      myEditor.getScrollingModel().disableAnimation();
      int scrollTo = myEditor.visibleLineNumberToYPosition(myEditor.getCaretModel().getVisualPosition().line) - mySavedCaretShift;
      myEditor.getScrollingModel().scrollVertically(scrollTo);
      myEditor.getScrollingModel().enableAnimation();
    }
  }

  public void rebuild() {
    myFoldTree.rebuild();
  }

  private void updateCachedOffsets() {
    myFoldTree.updateCachedOffsets();
  }

  public int getFoldedLinesCountBefore(int offset) {
    return myFoldTree.getFoldedLinesCountBefore(offset);
  }

  FoldRegion[] fetchTopLevel() {
    return myFoldTree.fetchTopLevel();
  }

  FoldRegion fetchOutermost(int offset) {
    return myFoldTree.fetchOutermost(offset);
  }

  public FoldRegion[] fetchCollapsedAt(int offset) {
    return myFoldTree.fetchCollapsedAt(offset);
  }

  public boolean intersectsRegion (int startOffset, int endOffset) {
    return myFoldTree.intersectsRegion(startOffset, endOffset);
  }

  public FoldRegion[] fetchVisible() {
    return myFoldTree.fetchVisible();
  }

  public int getLastCollapsedRegionBefore(int offset) {
    return myFoldTree.getLastTopLevelIndexBefore(offset);
  }

  public TextAttributes getPlaceholderAttributes() {
    return myFoldTextAttributes;
  }

  public void flushCaretPosition() {
    myCaretPositionSaved = false;
  }

  class FoldRegionsTree {
    private FoldRegion[] myCachedVisible;
    private FoldRegion[] myCachedTopLevelRegions;
    private int[] myCachedEndOffsets;
    private int[] myCachedStartOffsets;
    private int[] myCachedFoldedLines;
    private LinkedList<FoldRegion> myRegions;  //sorted in tree left-to-right topdown traversal order

    public FoldRegionsTree() {
      myCachedVisible = null;
      myRegions = new LinkedList<FoldRegion>();
    }

    public boolean isFoldingEnabled() {
      return FoldingModelImpl.this.isFoldingEnabled() && myCachedVisible != null;
    }

    public void rebuild() {
      ArrayList<FoldRegion> topLevels = new ArrayList<FoldRegion>(myRegions.size() / 2);
      ArrayList<FoldRegion> visible = new ArrayList<FoldRegion>(myRegions.size());
      FoldRegion[] regions = myRegions.toArray(new FoldRegion[myRegions.size()]);
      FoldRegion currentToplevel = null;
      for (int i = 0; i < regions.length; i++) {
        FoldRegion region = regions[i];
        if (region.isValid()) {
          visible.add(region);
          if (!region.isExpanded()) {
            if (currentToplevel == null || currentToplevel.getEndOffset() < region.getStartOffset()) {
              currentToplevel = region;
              topLevels.add(region);
            }
          }
        }
      }

      myCachedTopLevelRegions = topLevels.toArray(new FoldRegion[topLevels.size()]);

      Arrays.sort(myCachedTopLevelRegions, new Comparator() {
        public int compare(Object o1, Object o2) {
          FoldRegion r1 = (FoldRegion) o1;
          FoldRegion r2 = (FoldRegion) o2;
          int end1 = r1.getEndOffset();
          int end2 = r2.getEndOffset();
          if (end1 < end2) return -1;
          if (end1 > end2) return 1;
          return 0;
        }
      });

      FoldRegion[] visibleArrayed = visible.toArray(new FoldRegion[visible.size()]);
      for (int i = 0; i < visibleArrayed.length; i++) {
        FoldRegion visibleRegion = visibleArrayed[i];
        for (int j = 0; j < myCachedTopLevelRegions.length; j++) {
          FoldRegion topLevelRegion = myCachedTopLevelRegions[j];
          if (contains(topLevelRegion, visibleRegion)) {
            visible.remove(visibleRegion);
            break;
          }
        }
      }

      myCachedVisible = visible.toArray(new FoldRegion[visible.size()]);

      Arrays.sort(myCachedVisible, new Comparator() {
        public int compare(Object o1, Object o2) {
          FoldRegion r1 = (FoldRegion) o1;
          FoldRegion r2 = (FoldRegion) o2;
          int end1 = r1.getEndOffset();
          int end2 = r2.getEndOffset();
          if (end1 < end2) return 1;
          if (end1 > end2) return -1;
          return 0;
        }
      });

      updateCachedOffsets();
    }

    public void updateCachedOffsets() {
      if (isFoldingEnabled()) {
        for (int i = 0; i < myCachedVisible.length; i++) {
          FoldRegion foldRegion = myCachedVisible[i];
          if (!foldRegion.isValid()) {
            rebuild();
            return;
          }
        }

        int sum = 0;

        if (myCachedEndOffsets == null || myCachedEndOffsets.length != myCachedTopLevelRegions.length) {
          myCachedEndOffsets = new int[myCachedTopLevelRegions.length];
          myCachedStartOffsets = new int[myCachedTopLevelRegions.length];
          myCachedFoldedLines = new int[myCachedTopLevelRegions.length];
        }

        for (int i = 0; i < myCachedTopLevelRegions.length; i++) {
          FoldRegion region = myCachedTopLevelRegions[i];
          myCachedStartOffsets[i] = region.getStartOffset();
          myCachedEndOffsets[i] = region.getEndOffset() - 1;
          sum += region.getDocument().getLineNumber(region.getEndOffset()) - region.getDocument().getLineNumber(region.getStartOffset());
          myCachedFoldedLines[i] = sum;
        }
      }
    }

    public boolean addRegion(FoldRegion range) {
      for (int i = 0; i < myRegions.size(); i++) {
        FoldRegion region = myRegions.get(i);
        if (region.isValid() && intersects(region, range)) {
          return false;
        }

        if (range.getStartOffset() < region.getStartOffset() ||
            (range.getStartOffset() == region.getStartOffset() && range.getEndOffset() > region.getEndOffset())) {
          for (int j = i + 1; j < myRegions.size(); j++) {
            FoldRegion next = myRegions.get(j);
            if (next.getEndOffset() >= range.getEndOffset() && next.isValid()) {
              if (next.getStartOffset() < range.getStartOffset()) {
                return false;
              } else break;
            }
          }

          myRegions.add(i, range);
          return true;
        }
      }
      myRegions.addLast(range);
      return true;
    }

    public FoldRegion fetchOutermost(int offset) {
      if (!isFoldingEnabled()) return null;
      int start = 0;
      int end = myCachedEndOffsets.length - 1;

      while (start <= end) {
        int i = (start + end) / 2;
        if (offset < myCachedStartOffsets[i]) {
          end = i - 1;
        } else if (offset > myCachedEndOffsets[i]) {
          start = i + 1;
        } else return myCachedTopLevelRegions[i];
      }

      return null;
    }

    public FoldRegion[] fetchVisible() {
      if (!isFoldingEnabled()) return new FoldRegion[0];
      return myCachedVisible;
    }

    public FoldRegion[] fetchTopLevel() {
      if (!isFoldingEnabled()) return null;
      return myCachedTopLevelRegions;
    }

    private boolean contains(FoldRegion outer, FoldRegion inner) {
      return outer.getStartOffset() < inner.getStartOffset() && outer.getEndOffset() > inner.getStartOffset();
    }

    private boolean intersects(FoldRegion r1, FoldRegion r2) {
      final int s1 = r1.getStartOffset();
      final int s2 = r2.getStartOffset();
      final int e1 = r1.getEndOffset();
      final int e2 = r2.getEndOffset();
      return s1 == s2 && e1 == e2 ||
             s1 < s2 && s2 < e1 && e1 < e2 ||
             s2 < s1 && s1 < e2 && e2 < e1;
    }

    private boolean contains(FoldRegion region, int offset) {
      return region.getStartOffset() <= offset && region.getEndOffset() > offset;
    }

    public FoldRegion[] fetchCollapsedAt(int offset) {
      if (!isFoldingEnabled()) return new FoldRegion[0];
      ArrayList<FoldRegion> allCollapsed = new ArrayList<FoldRegion>();
      for (int i = 0; i < myRegions.size(); i++) {
        FoldRegion region = myRegions.get(i);
        if (!region.isExpanded() && contains(region, offset)) {
          allCollapsed.add(region);
        }
      }

      return allCollapsed.toArray(new FoldRegion[allCollapsed.size()]);
    }

    public boolean intersectsRegion(int startOffset, int endOffset) {
      if (!FoldingModelImpl.this.isFoldingEnabled()) return true;
      for (int i = 0; i < myRegions.size(); i++) {
        FoldRegion region = myRegions.get(i);
        boolean contains1 = contains(region, startOffset), contains2 = contains(region, endOffset);
        if ((contains1 && !contains2) || (!contains1 && contains2)) {
          return true;
        }
      }
      return false;
    }

    public FoldRegion[] fetchAllRegions() {
      if (!isFoldingEnabled()) return new FoldRegion[0];

      return myRegions.toArray(new FoldRegion[myRegions.size()]);
    }

    public void removeRegion(FoldRegion range) {
      myRegions.remove(range);
    }

    public int getFoldedLinesCountBefore(int offset) {
      int idx = getLastTopLevelIndexBefore(offset);
      if (idx == -1) return 0;
      return myCachedFoldedLines[idx];
    }

    public int getLastTopLevelIndexBefore(int offset) {
      if (!isFoldingEnabled()) return -1;

      int start = 0;
      int end = myCachedEndOffsets.length - 1;

      while (start <= end) {
        int i = (start + end) / 2;
        if (offset < myCachedEndOffsets[i]) {
          end = i - 1;
        } else if (offset > myCachedEndOffsets[i]) {
          start = i + 1;
        } else return i;
      }

      return end;

//      for (int i = 0; i < myCachedEndOffsets.length; i++) {
//        if (!myCachedTopLevelRegions[i].isValid()) continue;
//        int endOffset = myCachedEndOffsets[i];
//        if (endOffset > offset) break;
//        lastIndex = i;
//      }
//
//      return lastIndex;
    }

    public FoldRegion[] fetchAllRegionsIncludingInvalid() {
      if (!FoldingModelImpl.this.isFoldingEnabled()) return new FoldRegion[0];

      return myRegions.toArray(new FoldRegion[myRegions.size()]);
    }
  }

  public void beforeDocumentChange(DocumentEvent event) {
  }

  public void documentChanged(DocumentEvent event) {
    updateCachedOffsets();
  }
}
