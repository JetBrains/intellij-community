/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.palette;

import com.intellij.designer.componentTree.TreeTransfer;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicListUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * @author Alexander Lobas
 */
public class PaletteItemsComponent extends JBList {
  private static final SimpleTextAttributes DEPRECATED_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null);

  private final PaletteGroup myGroup;
  private final DesignerEditorPanel myDesigner;
  private int myBeforeClickSelectedRow = -1;
  private boolean myNeedClearSelection;
  private Integer myTempWidth;

  public PaletteItemsComponent(PaletteGroup group, @Nullable DesignerEditorPanel designer) {
    myGroup = group;
    myDesigner = designer;

    setModel(new AbstractListModel() {
      @Override
      public int getSize() {
        return myGroup.getItems().size();
      }

      @Override
      public Object getElementAt(int index) {
        return myGroup.getItems().get(index);
      }
    });

    ColoredListCellRenderer renderer = new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        clear();
        PaletteItem item = (PaletteItem)value;

        boolean enabled = item.isEnabled();
        setEnabled(enabled);

        if (enabled) {
          setIcon(item.getIcon());
        }
        else {
          setIcon(IconLoader.getDisabledIcon(item.getIcon()));
        }

        String title = item.getTitle();
        String tooltip = item.getTooltip();
        String version = myDesigner.getVersionLabel(item.getVersion());
        String deprecatedIn = item.getDeprecatedIn();
        boolean deprecated = myDesigner.isDeprecated(deprecatedIn);

        append(title, deprecated ? DEPRECATED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);

        if (!version.isEmpty()) {
          version = "<sup><i>" + version + "</i></sup>";
        }
        if (tooltip != null) {
          String deprecatedMessage = "";
          if (deprecated) {
            deprecatedMessage =
              String.format("<b>This item is deprecated in version \"%1$s\".<br>", myDesigner.getVersionLabel(deprecatedIn));
            String hint = item.getDeprecatedHint();
            if (!StringUtil.isEmpty(hint)) {
              deprecatedMessage += hint;
            }
            deprecatedMessage += "</b><br><br>";
          }

          tooltip = "<html><body><center><b>" +
                    StringUtil.escapeXml(title) +
                    "</b>" +
                    version +
                    "</center><p style='width: 300px'>" +
                    deprecatedMessage +
                    tooltip +
                    "</p></body></html>";
        }
        setToolTipText(tooltip);
      }
    };
    renderer.getIpad().left = 2 * UIUtil.getTreeLeftChildIndent();
    renderer.getIpad().right = UIUtil.getTreeRightChildIndent();
    setCellRenderer(renderer);

    setVisibleRowCount(0);
    setLayoutOrientation(HORIZONTAL_WRAP);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myNeedClearSelection = SwingUtilities.isLeftMouseButton(e) &&
                               myBeforeClickSelectedRow >= 0 &&
                               locationToIndex(e.getPoint()) == myBeforeClickSelectedRow &&
                               !UIUtil.isControlKeyDown(e) && !e.isShiftDown();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) &&
            myBeforeClickSelectedRow >= 0 &&
            locationToIndex(e.getPoint()) == myBeforeClickSelectedRow &&
            !UIUtil.isControlKeyDown(e) && !e.isShiftDown() && myNeedClearSelection) {
          clearSelection();
        }
      }
    });

    setDragEnabled(true);
    setTransferHandler(new TreeTransfer(PaletteItem.class));
    DnDManager.getInstance().registerSource(new DnDSource() {
      @Override
      public boolean canStartDragging(DnDAction action, Point dragOrigin) {
        int index = locationToIndex(dragOrigin);
        if (index != -1 && myDesigner != null) {
          PaletteItem paletteItem = myGroup.getItems().get(index);
          myDesigner.activatePaletteItem(paletteItem);
        }
        return false;
      }

      @Override
      public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
        return null;
      }

      @Nullable
      @Override
      public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
        return null;
      }

      @Override
      public void dragDropEnd() {
      }

      @Override
      public void dropActionChanged(int gestureModifiers) {
      }
    }, this);

    initActions();
  }

  @Override
  public void updateUI() {
    setUI(new BasicListUI() {
      MouseListener myListener;

      @Override
      protected void updateLayoutState() {
        super.updateLayoutState();

        Insets insets = list.getInsets();
        int listWidth = list.getWidth() - (insets.left + insets.right);
        if (listWidth >= cellWidth) {
          int columnCount = listWidth / cellWidth;
          cellWidth = (columnCount == 0) ? 1 : listWidth / columnCount;
        }
      }

      @Override
      protected void installListeners() {
        addMouseListener(myListener = new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            myBeforeClickSelectedRow = list.getSelectedIndex();
          }
        });
        super.installListeners();
      }

      @Override
      protected void uninstallListeners() {
        if (myListener != null) {
          removeMouseListener(myListener);
        }
        super.uninstallListeners();
      }
    });
    invalidate();
  }

  public int getWidth() {
    return (myTempWidth == null) ? super.getWidth() : myTempWidth.intValue();
  }

  public int getPreferredHeight(int width) {
    myTempWidth = width;
    try {
      return getUI().getPreferredSize(this).height;
    }
    finally {
      myTempWidth = null;
    }
  }

  public void takeFocusFrom(int indexToSelect) {
    if (indexToSelect == -1) {
      indexToSelect = getModel().getSize() - 1;
    }
    else if (getModel().getSize() == 0) {
      indexToSelect = -1;
    }
    requestFocus();
    setSelectedIndex(indexToSelect);
    if (indexToSelect >= 0) {
      ensureIndexIsVisible(indexToSelect);
    }
  }

  public void restoreSelection(PaletteItem paletteItem) {
    if (paletteItem == null) {
      clearSelection();
    }
    else {
      int index = myGroup.getItems().indexOf(paletteItem);
      if (index == -1) {
        clearSelection();
      }
      else {
        takeFocusFrom(index);
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private void initActions() {
    ActionMap map = getActionMap();
    map.put("selectPreviousRow", new MoveFocusAction(map.get("selectPreviousRow"), false));
    map.put("selectNextRow", new MoveFocusAction(map.get("selectNextRow"), true));
    map.put("selectPreviousColumn", new MoveFocusAction(new ChangeColumnAction(map.get("selectPreviousColumn"), false), false));
    map.put("selectNextColumn", new MoveFocusAction(new ChangeColumnAction(map.get("selectNextColumn"), true), true));
  }

  private class MoveFocusAction extends AbstractAction {
    private final Action myDefaultAction;
    private final boolean myFocusNext;

    public MoveFocusAction(Action defaultAction, boolean focusNext) {
      myDefaultAction = defaultAction;
      myFocusNext = focusNext;
    }

    public void actionPerformed(ActionEvent e) {
      int selIndexBefore = getSelectedIndex();
      myDefaultAction.actionPerformed(e);
      int selIndexCurrent = getSelectedIndex();
      if (selIndexBefore != selIndexCurrent) {
        return;
      }
      if (myFocusNext && selIndexCurrent == 0) {
        return;
      }

      KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      Container container = kfm.getCurrentFocusCycleRoot();
      FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
      if (policy == null) {
        policy = kfm.getDefaultFocusTraversalPolicy();
      }
      Component next = myFocusNext
                       ? policy.getComponentAfter(container, PaletteItemsComponent.this)
                       : policy.getComponentBefore(container, PaletteItemsComponent.this);
      if (next instanceof PaletteGroupComponent) {
        clearSelection();
        next.requestFocus();
        ((PaletteGroupComponent)next).scrollRectToVisible(next.getBounds());
      }
    }
  }

  private class ChangeColumnAction extends AbstractAction {
    private final Action myDefaultAction;
    private final boolean mySelectNext;

    public ChangeColumnAction(Action defaultAction, boolean selectNext) {
      myDefaultAction = defaultAction;
      mySelectNext = selectNext;
    }

    public void actionPerformed(ActionEvent e) {
      int selIndexBefore = getSelectedIndex();
      myDefaultAction.actionPerformed(e);
      int selIndexCurrent = getSelectedIndex();
      if (mySelectNext && selIndexBefore < selIndexCurrent || !mySelectNext && selIndexBefore > selIndexCurrent) {
        return;
      }

      if (mySelectNext) {
        if (selIndexCurrent == selIndexBefore + 1) {
          selIndexCurrent++;
        }
        if (selIndexCurrent < getModel().getSize() - 1) {
          setSelectedIndex(selIndexCurrent + 1);
          scrollRectToVisible(getCellBounds(selIndexCurrent + 1, selIndexCurrent + 1));
        }
      }
      else if (selIndexCurrent > 0) {
        setSelectedIndex(selIndexCurrent - 1);
        scrollRectToVisible(getCellBounds(selIndexCurrent - 1, selIndexCurrent - 1));
      }
    }
  }
}