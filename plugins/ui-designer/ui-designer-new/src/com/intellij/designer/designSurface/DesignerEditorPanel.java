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
package com.intellij.designer.designSurface;

import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.designSurface.tools.SelectionTool;
import com.intellij.designer.designSurface.tools.ToolProvider;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class DesignerEditorPanel extends JPanel implements DataProvider {
  protected static final Integer LAYER_COMPONENT = JLayeredPane.DEFAULT_LAYER;
  protected static final Integer LAYER_STATIC_DECORATION = JLayeredPane.POPUP_LAYER;
  protected static final Integer LAYER_DECORATION = JLayeredPane.DRAG_LAYER;
  protected static final Integer LAYER_FEEDBACK = LAYER_DECORATION + 100;
  protected static final Integer LAYER_GLASS = LAYER_FEEDBACK + 100;
  protected static final Integer LAYER_BUTTONS = LAYER_GLASS + 100;
  protected static final Integer LAYER_INPLACE_EDITING = LAYER_BUTTONS + 100;

  @NonNls private final static String DESIGNER_CARD = "designer";
  @NonNls private final static String ERROR_CARD = "error";

  @NotNull protected final Module myModule;
  @NotNull protected final VirtualFile myFile;

  private final CardLayout myLayout = new CardLayout();
  private JPanel myDesignerCard;
  private CaptionPanel myHorizontalCaption;
  private CaptionPanel myVerticalCaption;
  private JScrollPane myScrollPane;
  protected JLayeredPane myLayeredPane;
  protected GlassLayer myGlassLayer;
  private DecorationLayer myDecorationLayer;
  private FeedbackLayer myFeedbackLayer;

  protected ToolProvider myToolProvider;
  protected EditableArea mySurfaceArea;

  private JLabel myErrorLabel;

  protected RadComponent myRootComponent;

  public DesignerEditorPanel(@NotNull Module module, @NotNull VirtualFile file) {
    myModule = module;
    myFile = file;

    setLayout(myLayout);
    createDesignerCard();
    createErrorCard();

    myToolProvider.loadDefaultTool();
  }

  private void createDesignerCard() {
    myDesignerCard = new JPanel(new GridBagLayout());
    add(myDesignerCard, DESIGNER_CARD);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.BOTH;

    myVerticalCaption = new CaptionPanel(this, false);
    myDesignerCard.add(myVerticalCaption, gbc);

    gbc.gridx = 1;
    gbc.gridy = 0;

    myHorizontalCaption = new CaptionPanel(this, true);
    myDesignerCard.add(myHorizontalCaption, gbc);

    myLayeredPane = new MyLayeredPane();

    mySurfaceArea = new ComponentEditableArea(myLayeredPane) {
      @Override
      protected void fireSelectionChanged() {
        super.fireSelectionChanged();
        myLayeredPane.revalidate();
        myLayeredPane.repaint();
      }

      @Override
      public RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
        if (myRootComponent != null) {
          FindComponentVisitor visitor = new FindComponentVisitor(filter, x, y);
          myRootComponent.accept(visitor, false);
          return visitor.getResult();
        }
        return null;
      }

      @Override
      public InputTool findTargetTool(int x, int y) {
        return myDecorationLayer.findTargetTool(x, y);
      }

      @Override
      public ComponentDecorator getRootSelectionDecorator() {
        return DesignerEditorPanel.this.getRootSelectionDecorator();
      }

      @Nullable
      public EditOperation processRootOperation(OperationContext context) {
        return DesignerEditorPanel.this.processRootOperation(context);
      }

      @Override
      public FeedbackLayer getFeedbackLayer() {
        return myFeedbackLayer;
      }

      @Override
      public RadComponent getRootComponent() {
        return myRootComponent;
      }
    };

    myToolProvider = new ToolProvider() {
      @Override
      public void loadDefaultTool() {
        setActiveTool(new SelectionTool());
      }

      @Override
      public void showError(@NonNls String message, Throwable e) {
        DesignerEditorPanel.this.showError(message, e);
      }
    };

    myGlassLayer = new GlassLayer(myToolProvider, mySurfaceArea);
    myLayeredPane.add(myGlassLayer, LAYER_GLASS);

    myDecorationLayer = new DecorationLayer(mySurfaceArea);
    myLayeredPane.add(myDecorationLayer, LAYER_DECORATION);

    myFeedbackLayer = new FeedbackLayer();
    myLayeredPane.add(myFeedbackLayer, LAYER_FEEDBACK);

    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.weightx = 1;
    gbc.weighty = 1;

    myScrollPane = ScrollPaneFactory.createScrollPane(myLayeredPane);
    myScrollPane.setBackground(Color.WHITE);
    myDesignerCard.add(myScrollPane, gbc);
  }

  private void createErrorCard() {
    myErrorLabel = new JLabel(IconLoader.getIcon("/ide/error_notifications.png"), SwingConstants.CENTER);
    add(myErrorLabel, ERROR_CARD);
  }

  protected final void showDesignerCard() {
    myLayout.show(this, DESIGNER_CARD);
  }

  public EditableArea getSurfaceArea() {
    return mySurfaceArea;
  }

  public ToolProvider getToolProvider() {
    return myToolProvider;
  }

  protected abstract ComponentDecorator getRootSelectionDecorator();

  @Nullable
  protected abstract EditOperation processRootOperation(OperationContext context);

  public void showError(@NonNls String message, Throwable e) {
    myRootComponent = null;
    myErrorLabel.setText(message + e.toString());
    myLayout.show(this, ERROR_CARD);
    DesignerToolWindowManager.getInstance(myModule.getProject()).refresh();
    repaint();
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      e.printStackTrace();
    }
  }

  public void activate() {
  }

  public void deactivate() {
  }

  @Override
  public Object getData(@NonNls String dataId) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public void dispose() {
    // TODO: Auto-generated method stub
  }

  public JComponent getPreferredFocusedComponent() {
    return myDesignerCard.isVisible() ? myGlassLayer : myErrorLabel;
  }

  public Object[] getTreeRoots() {
    return myRootComponent == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : new Object[]{myRootComponent};
  }

  public abstract TreeComponentDecorator getTreeDecorator();

  private final class MyLayeredPane extends JLayeredPane implements Scrollable {
    public void doLayout() {
      for (int i = getComponentCount() - 1; i >= 0; i--) {
        final Component component = getComponent(i);
        component.setBounds(0, 0, getWidth(), getHeight());
      }
    }

    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    public Dimension getPreferredSize() {
      int width = 0;
      int height = 0;

      if (myRootComponent != null) {
        width = Math.max(width, (int)myRootComponent.getBounds().getMaxX());
        height = Math.max(height, (int)myRootComponent.getBounds().getMaxY());

        for (RadComponent component : myRootComponent.getChildren()) {
          width = Math.max(width, (int)component.getBounds().getMaxX());
          height = Math.max(height, (int)component.getBounds().getMaxY());
        }
      }

      width += 50;
      height += 40;

      Rectangle bounds = myScrollPane.getViewport().getBounds();

      return new Dimension(Math.max(width, bounds.width), Math.max(height, bounds.height));
    }

    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return 10;
    }

    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      if (orientation == SwingConstants.HORIZONTAL) {
        return visibleRect.width - 10;
      }
      return visibleRect.height - 10;
    }

    public boolean getScrollableTracksViewportWidth() {
      return false;
    }

    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private class FindComponentVisitor extends RadComponentVisitor {
    @Nullable private final ComponentTargetFilter myFilter;
    private RadComponent myResult;
    private final int myX;
    private final int myY;

    public FindComponentVisitor(@Nullable ComponentTargetFilter filter, int x, int y) {
      myFilter = filter;
      myX = x;
      myY = y;
    }

    public RadComponent getResult() {
      return myResult;
    }

    @Override
    public boolean visit(RadComponent component) {
      return myResult == null &&
             component.getBounds(myLayeredPane).contains(myX, myY) &&
             (myFilter == null || myFilter.preFilter(component));
    }

    @Override
    public void endVisit(RadComponent component) {
      if (myResult == null && (myFilter == null || myFilter.resultFilter(component))) {
        myResult = component;
      }
    }
  }
}