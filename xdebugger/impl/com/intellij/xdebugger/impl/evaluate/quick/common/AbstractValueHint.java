package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.Tree;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;

/**
 * @author nik
 */
public abstract class AbstractValueHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint");
  @NonNls private final static String DIMENSION_SERVICE_KEY = "DebuggerActiveHint";
  private static final Icon COLLAPSED_TREE_ICON = IconLoader.getIcon("/general/add.png");
  private static final int HINT_TIMEOUT = 7000; // ms
  private final KeyListener myEditorKeyListener = new KeyAdapter() {
    public void keyReleased(KeyEvent e) {
      if(!isAltMask(e.getModifiers())) {
        ValueLookupManager.getInstance(myProject).hideHint();
      }
    }
  };
  private static TextAttributes ourReferenceAttributes = new TextAttributes();
  static {
    ourReferenceAttributes.setForegroundColor(Color.blue);
    ourReferenceAttributes.setEffectColor(Color.blue);
    ourReferenceAttributes.setEffectType(EffectType.LINE_UNDERSCORE);
  }

  private RangeHighlighter myHighlighter;
  private Cursor myStoredCursor;
  private final Project myProject;
  private final Editor myEditor;
  private final ValueHintType myType;
  private Point myPoint;
  private LightweightHint myCurrentHint;
  private JBPopup myPopup;
  private boolean myHintHidden;
  private TextRange myCurrentRange;

  public AbstractValueHint(Project project, Editor editor, Point point, ValueHintType type, final TextRange textRange) {
    myPoint = point;
    myProject = project;
    myEditor = editor;
    myType = type;
    myCurrentRange = textRange;
  }

  protected abstract boolean canShowHint();

  protected abstract void evaluateAndShowHint();

  private void resize(final TreePath path, JTree tree) {
    if (myPopup == null || !myPopup.isVisible()) return;
    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    if (popupWindow == null) return;
    final Dimension size = tree.getPreferredSize();
    final Point location = popupWindow.getLocation();
    final Rectangle windowBounds = popupWindow.getBounds();
    final Rectangle bounds = tree.getPathBounds(path);
    if (bounds == null) return;

    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.max(Math.max(size.width, bounds.width) + 20, windowBounds.width),
                                                 Math.max(tree.getRowCount() * bounds.height + 55, windowBounds.height));
    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    popupWindow.setBounds(targetBounds);
    popupWindow.validate();
    popupWindow.repaint();
  }

  private void updateInitialBounds(final Tree tree) {
    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    final Dimension size = tree.getPreferredSize();
    final Point location = popupWindow.getLocation();
    final Rectangle windowBounds = popupWindow.getBounds();
    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.max(size.width + 250, windowBounds.width),
                                                 Math.max(size.height, windowBounds.height));
    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    popupWindow.setBounds(targetBounds);
    popupWindow.validate();
    popupWindow.repaint();
  }

  public boolean isKeepHint(Editor editor, Point point) {
    if(myType == ValueHintType.MOUSE_ALT_OVER_HINT) {
      return false;
    }
    else if(myType == ValueHintType.MOUSE_CLICK_HINT) {
      if(myCurrentHint != null && myCurrentHint.isVisible()) {
        return true;
      }
    }
    else {
      int offset = calculateOffset(editor, point);

      if (myCurrentRange != null && myCurrentRange.getStartOffset() <= offset && offset <= myCurrentRange.getEndOffset()) {
        return true;
      }
    }
    return false;
  }

  public static int calculateOffset(@NotNull Editor editor, @NotNull Point point) {
    LogicalPosition pos = editor.xyToLogicalPosition(point);
    return editor.logicalPositionToOffset(pos);
  }

  public void hideHint() {
    myHintHidden = true;
    myCurrentRange = null;
    if(myStoredCursor != null) {
      Component internalComponent = myEditor.getContentComponent();
      internalComponent.setCursor(myStoredCursor);
      if (LOG.isDebugEnabled()) {
        LOG.debug("internalComponent.setCursor(myStoredCursor)");
      }
      internalComponent.removeKeyListener(myEditorKeyListener);
    }

    if(myCurrentHint != null) {
      myCurrentHint.hide();
      myCurrentHint = null;
    }
    if(myHighlighter != null) {
      myEditor.getMarkupModel().removeHighlighter(myHighlighter);
      myHighlighter = null;
    }
  }

  public void invokeHint() {
    if(!canShowHint()) {
      hideHint();
      return;
    }

    if (myType == ValueHintType.MOUSE_ALT_OVER_HINT) {
      myHighlighter = myEditor.getMarkupModel().addRangeHighlighter(myCurrentRange.getStartOffset(), myCurrentRange.getEndOffset(),
                                                                    HighlighterLayer.SELECTION + 1, ourReferenceAttributes,
                                                                    HighlighterTargetArea.EXACT_RANGE);
      Component internalComponent = myEditor.getContentComponent();
      myStoredCursor = internalComponent.getCursor();
      internalComponent.addKeyListener(myEditorKeyListener);
      internalComponent.setCursor(hintCursor());
      if (LOG.isDebugEnabled()) {
        LOG.debug("internalComponent.setCursor(hintCursor())");
      }
    }
    else {
      evaluateAndShowHint();
    }
  }

  private static Cursor hintCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  public void shiftLocation() {
    if (myPopup != null) {
      final Window window = SwingUtilities.getWindowAncestor(myPopup.getContent());
      if (window != null) {
        myPoint = new RelativePoint(window, new Point(2, 2)).getPoint(myEditor.getContentComponent());
      }
    }
  }

  public Project getProject() {
    return myProject;
  }

  protected Editor getEditor() {
    return myEditor;
  }

  protected ValueHintType getType() {
    return myType;
  }

  public void showTreePopup(final AbstractValueHintTreeComponent<?> component, final Tree tree, final String title) {
    if (myPopup != null) {
      myPopup.cancel();
    }
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(component.getMainPanel(), tree)
      .setRequestFocus(true)
      .setTitle(title)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(getProject(), DIMENSION_SERVICE_KEY, false)
      .createPopup();

    //Editor may be disposed before later invokator process this action
    if (getEditor().getComponent().getRootPane() == null) return;
    myPopup.show(new RelativePoint(getEditor().getContentComponent(), myPoint));

    updateInitialBounds(tree);
  }

  protected boolean showHint(final JComponent component) {
    myCurrentHint = new LightweightHint(component);
    HintManager hintManager = HintManager.getInstance();

    //Editor may be disposed before later invokator process this action
    if(getEditor().getComponent().getRootPane() == null) return false;

    Point p = HintManager.getHintPosition(myCurrentHint, getEditor(), getEditor().xyToLogicalPosition(myPoint), HintManager.UNDER);
    hintManager.showEditorHint(myCurrentHint, getEditor(), p,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, HINT_TIMEOUT, false);
    return true;
  }

  protected boolean isHintHidden() {
    return myHintHidden;
  }

  protected JComponent createExpandableHintComponent(final SimpleColoredText text, final Runnable expand) {
    final JComponent component = HintUtil.createInformationLabel(text, COLLAPSED_TREE_ICON);
    addMouseListenerToHierarchy(component, new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (myCurrentHint != null) {
          myCurrentHint.hide();
        }
        expand.run();
      }
    });
    return component;
  }

  private static void addMouseListenerToHierarchy(Component c, MouseListener l) {
    c.addMouseListener(l);
    if (c instanceof Container) {
      final Container container = (Container)c;
      Component[] children = container.getComponents();
      for (Component child : children) {
        addMouseListenerToHierarchy(child, l);
      }
    }
  }

  protected Point getPoint() {
    return myPoint;
  }

  protected TextRange getCurrentRange() {
    return myCurrentRange;
  }

  protected TreeModelListener createTreeListener(final Tree tree) {
    return new TreeModelListener() {
      public void treeNodesChanged(TreeModelEvent e) {
        //do nothing
      }

      public void treeNodesInserted(TreeModelEvent e) {
        //do nothing
      }

      public void treeNodesRemoved(TreeModelEvent e) {
        //do nothing
      }

      public void treeStructureChanged(TreeModelEvent e) {
        resize(e.getTreePath(), tree);
      }
    };
  }

  private static boolean isAltMask(int modifiers) {
    return modifiers == InputEvent.ALT_MASK;
  }

  public static ValueHintType getType(final EditorMouseEvent e) {
    return isAltMask(e.getMouseEvent().getModifiers()) ? ValueHintType.MOUSE_ALT_OVER_HINT : ValueHintType.MOUSE_OVER_HINT;
  }
}
