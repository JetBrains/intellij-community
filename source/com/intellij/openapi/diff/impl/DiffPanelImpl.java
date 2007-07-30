package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.actions.IgnoreWhiteSpacesAction;
import com.intellij.openapi.diff.actions.MergeActionGroup;
import com.intellij.openapi.diff.actions.NextDiffAction;
import com.intellij.openapi.diff.actions.PreviousDiffAction;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.ex.DiffPanelOptions;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.fragments.FragmentList;
import com.intellij.openapi.diff.impl.highlighting.DiffPanelState;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.splitter.DiffDividerPaint;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.diff.impl.util.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.security.InvalidParameterException;

public class DiffPanelImpl implements DiffPanelEx, ContentChangeListener, TwoSidesContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.DiffPanelImpl");

  private final DiffSplitter mySplitter;
  private final DiffPanelOutterComponent myPanel;

  private final Window myOwnerWindow;
  private final DiffPanelOptions myOptions;

  private final DiffPanelState myData;

  private final Rediffers myDiffUpdater;
  private final DiffSideView myLeftSide;
  private final DiffSideView myRightSide;
  private DiffSideView myCurrentSide;
  private LineBlocks myLineBlocks = LineBlocks.EMPTY;
  private final SyncScrollSupport myScrollSupport = new SyncScrollSupport();
  private final FontSizeSynchronizer myFontSizeSynchronizer = new FontSizeSynchronizer();
  private final DiffRequest.ToolbarAddons TOOL_BAR = new DiffRequest.ToolbarAddons() {
    public void customize(DiffToolbar toolbar) {
      ActionManager actionManager = ActionManager.getInstance();
      toolbar.addAction(actionManager.getAction(IdeActions.ACTION_COPY));
      toolbar.addAction(actionManager.getAction(IdeActions.ACTION_FIND));
      toolbar.addAction(PreviousDiffAction.find());
      toolbar.addAction(NextDiffAction.find());
      toolbar.addSeparator();
      toolbar.addAction(new LabelAction(DiffBundle.message("comparison.ignore.whitespace.acton.name")));
      toolbar.addAction(new IgnoreWhiteSpacesAction(DiffPanelImpl.this));
    }
  };
  private boolean myDisposed = false;

  public DiffPanelImpl(final Window owner, Project project, boolean enableToolbar) {
    myOptions = new DiffPanelOptions(this);
    myPanel = new DiffPanelOutterComponent(TextDiffType.DIFF_TYPES, TOOL_BAR);
    myPanel.disableToolbar(!enableToolbar);
    if (enableToolbar) myPanel.resetToolbar();
    myOwnerWindow = owner;
    myLeftSide = new DiffSideView(DiffBundle.message("diff.left.side.default.title"), this);
    myRightSide = new DiffSideView(DiffBundle.message("diff.right.side.default.title"), this);
    myLeftSide.becomeMaster();
    myDiffUpdater = new Rediffers(this);

    myData = new DiffPanelState(this, project);
    mySplitter = new DiffSplitter(myLeftSide.getComponent(), myRightSide.getComponent(),
                                  new DiffDividerPaint(this, FragmentSide.SIDE1));
    myPanel.insertDiffComponent(mySplitter, new MyScrollingPanel());
    myPanel.setDataProvider(new MyDataProvider());

    final ComparisonPolicy comparisonPolicy = getComparisonPolicy();
    final ComparisonPolicy defaultComparisonPolicy = DiffManagerImpl.getInstanceEx().getComparisonPolicy();

    if (defaultComparisonPolicy != null && comparisonPolicy != defaultComparisonPolicy) {
      setComparisonPolicy(defaultComparisonPolicy);
    }


  }

  public Editor getEditor1() {
    return myLeftSide.getEditor();
  }

  public Editor getEditor2() {
    if (myDisposed) LOG.error("Disposed");
    Editor editor = myRightSide.getEditor();
    if (editor != null) return editor;
    if (myData.getContent2() == null) LOG.error("No content 2");
    return editor;
  }

  public void setContents(DiffContent content1, DiffContent content2) {
    LOG.assertTrue(!myDisposed);
    myData.setContents(content1, content2);
    Project project = myData.getProject();
    FileType[] types = DiffUtil.chooseContentTypes(new DiffContent[]{content1, content2});
    myLeftSide.setHighlighterFactory(createHighlighter(types[0], project));
    myRightSide.setHighlighterFactory(createHighlighter(types[1], project));
    rediff();
    myPanel.requestScrollEditors();
  }

  private DiffHighliterFactory createHighlighter(FileType contentType, Project project) {
    return new DiffHighliterFactoryImpl(contentType, project);
  }

  void rediff() {
    setLineBlocks(myData.updateEditors());
  }

  public void setTitle1(String title) {
    myLeftSide.setTitle(title);
  }

  public void setTitle2(String title) {
    myRightSide.setTitle(title);
  }

  private void setLineBlocks(LineBlocks blocks) {
    myLineBlocks = blocks;
    mySplitter.redrawDiffs();
    updateStatusBar();
  }

  public void invalidateDiff() {
    setLineBlocks(LineBlocks.EMPTY);
    myData.removeActions();
  }

  public FragmentList getFragments() {
    return myData.getFragmentList();
  }

  private int[] getFragmentBeginnings() {
    return getFragmentBeginnings(myCurrentSide.getSide());
  }

  int[] getFragmentBeginnings(FragmentSide side) {
    return getLineBlocks().getBegginings(side);
  }

  public void dispose() {
    myDisposed = true;
    myDiffUpdater.dispose();
    myScrollSupport.dispose();
    myData.dispose();
    myPanel.cancelScrollEditors();
    JComponent component = myPanel.getBottomComponent();
    if (component instanceof Disposable) {
      ((Disposable) component).dispose();
    }
    myPanel.setBottomComponent(null);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private void updateStatusBar() {
    int differentLineBlocks = getLineBlocks().getCount();
    String text;
    myPanel.setStatusBarText(DiffBundle.message("diff.count.differences.status.text", differentLineBlocks));
  }

  public boolean hasDifferences() {
    return getLineBlocks().getCount() > 0;
  }

  public JComponent getPreferredFocusedComponent() {
    return myCurrentSide.getFocusableComponent();
  }

  public ComparisonPolicy getComparisonPolicy() {
    return myData.getComparisonPolicy();
  }

  private void setComparisonPolicy(ComparisonPolicy policy, boolean notifyManager) {
    myData.setComparisonPolicy(policy);
    rediff();

    if (notifyManager) {
      DiffManagerImpl.getInstanceEx().setComparisonPolicy(policy);
    }
  }

  public void setComparisonPolicy(ComparisonPolicy comparisonPolicy) {
    setComparisonPolicy(comparisonPolicy, true);
  }

  public Rediffers getDiffUpdater() {
    return myDiffUpdater;
  }

  public void onContentChangedIn(EditorSource source) {
    myDiffUpdater.contentRemoved(source);
    final EditorEx editor = source.getEditor();
    if (source.getSide() == FragmentSide.SIDE1 && editor != null) {
      editor.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    }
    DiffSideView viewSide = getSideView(source.getSide());
    viewSide.setEditorSource(source);
    myScrollSupport.dispose();
    if (editor == null) {
      rediff();
      return;
    }
    PopupHandler.installUnknownPopupHandler(editor.getContentComponent(), new MergeActionGroup(this, source.getSide()), ActionManager.getInstance());
    myDiffUpdater.contentAdded(source);
    editor.getSettings().setLineNumbersShown(true);
    editor.getSettings().setFoldingOutlineShown(false);
    ((FoldingModelEx)editor.getFoldingModel()).setFoldingEnabled(false);
    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);

    Editor editor1 = getEditor(FragmentSide.SIDE1);
    Editor editor2 = getEditor(FragmentSide.SIDE2);
    if (editor1 != null && editor2 != null) {
      myScrollSupport.install(new EditingSides[]{this});
    }

    final VisibleAreaListener visibleAreaListener = mySplitter.getVisibleAreaListener();
    final ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.addVisibleAreaListener(visibleAreaListener);
    myFontSizeSynchronizer.synchronize(editor);
    source.addDisposable(new Disposable() {
      public void dispose() {
        myFontSizeSynchronizer.stopSynchronize(editor);
      }
    });
    source.addDisposable(new Disposable() {
      public void dispose() {
        scrollingModel.removeVisibleAreaListener(visibleAreaListener);
      }
    });
  }

  public void setCurrentSide(DiffSideView viewSide) {
    LOG.assertTrue(viewSide != myCurrentSide);
    LOG.assertTrue(viewSide != null);
    if (myCurrentSide != null) myCurrentSide.beSlave();
    myCurrentSide = viewSide;
  }

  private DiffSideView getCurrentSide() { return myCurrentSide; }

  public Project getProject() {
    return myData.getProject();
  }

  public void showSource(OpenFileDescriptor descriptor) {
    myOptions.showSource(descriptor);
  }

  public DiffPanelOptions getOptions() {
    return myOptions;
  }

  public Editor getEditor(FragmentSide side) {
    return getSideView(side).getEditor();
  }

  private DiffSideView getSideView(FragmentSide side) {
    if (side == FragmentSide.SIDE1) {
      return myLeftSide;
    }
    else if (side == FragmentSide.SIDE2) return myRightSide;
    throw new InvalidParameterException(String.valueOf(side));
  }

  public LineBlocks getLineBlocks() { return myLineBlocks; }

  public void setDiffRequest(DiffRequest data) {
    if (data.getHints().contains(DiffTool.HINT_DO_NOT_IGNORE_WHITESPACES)) {
      setComparisonPolicy(ComparisonPolicy.DEFAULT, false);
    }

    setContents(data.getContents()[0], data.getContents()[1]);
    setTitle1(data.getContentTitles()[0]);
    setTitle2(data.getContentTitles()[1]);
    setWindowTitle(myOwnerWindow, data.getWindowTitle());
    data.customizeToolbar(myPanel.resetToolbar());
    myPanel.registerToolbarActions();

    final JComponent oldBottomComponent = myPanel.getBottomComponent();
    if (oldBottomComponent instanceof Disposable) {
      ((Disposable) oldBottomComponent).dispose();
    }
    final JComponent newBottomComponent = data.getBottomComponent();
    myPanel.setBottomComponent(newBottomComponent);
  }

  private void setWindowTitle(Window window, String title) {
    if (window instanceof JDialog) {
      ((JDialog)window).setTitle(title);
    }
    else if (window instanceof JFrame) ((JFrame)window).setTitle(title);
  }

  @Nullable
  public static DiffPanelImpl fromDataContext(DataContext dataContext) {
    DiffViewer viewer = (DiffViewer)dataContext.getData(DataConstants.DIFF_VIEWER);
    return viewer instanceof DiffPanelImpl ? (DiffPanelImpl)viewer : null;
  }

  public Window getOwnerWindow() {
    return myOwnerWindow;
  }

  public void focusOppositeSide() {
    if (myCurrentSide == myLeftSide) {
      myRightSide.getEditor().getContentComponent().requestFocus();
    }
    else {
      myLeftSide.getEditor().getContentComponent().requestFocus();
    }
  }

  private class MyScrollingPanel implements DiffPanelOutterComponent.ScrollingPanel {

    public void scrollEditors() {
      getOptions().onNewContent(myCurrentSide);
      int[] fragments = getFragmentBeginnings();
      if (fragments.length > 0) myCurrentSide.scrollToFirstDiff(fragments[0]);
    }
  }

  private class MyDataProvider implements DataProvider {
    private final FocusDiffSide myFocusDiffSide = new FocusDiffSide() {
      public Editor getEditor() {
        return getCurrentSide().getEditor();
      }

      public int[] getFragmentStartingLines() {
        return getFragmentBeginnings();
      }
    };

    public Object getData(String dataId) {
      if (DataConstants.DIFF_VIEWER.equals(dataId)) return DiffPanelImpl.this;
      if (FocusDiffSide.FOCUSED_DIFF_SIDE.equals(dataId)) return myCurrentSide == null ? null : myFocusDiffSide;
      return null;
    }
  }

}
