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

import com.intellij.designer.DesignerEditorState;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.actions.DesignerActionPanel;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.componentTree.TreeEditableArea;
import com.intellij.designer.designSurface.tools.*;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.designer.palette.Item;
import com.intellij.designer.propertyTable.Property;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.diagnostic.errordialog.Attachment;
import com.intellij.ide.palette.impl.PaletteManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class DesignerEditorPanel extends JPanel implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.designer.designSurface.DesignerEditorPanel");

  protected static final Integer LAYER_COMPONENT = JLayeredPane.DEFAULT_LAYER;
  protected static final Integer LAYER_STATIC_DECORATION = JLayeredPane.POPUP_LAYER;
  protected static final Integer LAYER_DECORATION = JLayeredPane.DRAG_LAYER;
  protected static final Integer LAYER_FEEDBACK = LAYER_DECORATION + 100;
  protected static final Integer LAYER_GLASS = LAYER_FEEDBACK + 100;
  protected static final Integer LAYER_BUTTONS = LAYER_GLASS + 100;
  protected static final Integer LAYER_INPLACE_EDITING = LAYER_BUTTONS + 100;
  private static final Integer LAYER_PROGRESS = LAYER_INPLACE_EDITING + 100;

  private final static String DESIGNER_CARD = "designer";
  private final static String ERROR_CARD = "error";
  private final static String ERROR_STACK_CARD = "stack";
  private final static String ERROR_NO_STACK_CARD = "no_stack";

  protected final Module myModule;
  protected final VirtualFile myFile;

  private final CardLayout myLayout = new CardLayout();
  private JPanel myDesignerCard;

  protected DesignerActionPanel myActionPanel;

  private CaptionPanel myHorizontalCaption;
  private CaptionPanel myVerticalCaption;

  private JScrollPane myScrollPane;
  protected JLayeredPane myLayeredPane;
  protected GlassLayer myGlassLayer;
  private DecorationLayer myDecorationLayer;
  private FeedbackLayer myFeedbackLayer;

  private ListSelectionListener myPaletteListener;
  protected ToolProvider myToolProvider;
  protected EditableArea mySurfaceArea;

  protected RadComponent myRootComponent;

  private List<?> myExpandedComponents;
  private Property mySelectionProperty;
  private int[][] myExpandedState;
  private int[][] mySelectionState;

  private JPanel myErrorPanel;
  private JPanel myErrorMessages;
  private JPanel myErrorStackPanel;
  private CardLayout myErrorStackLayout;
  private JTextArea myErrorStack;

  private JPanel myProgressPanel;
  private AsyncProcessIcon myProgressIcon;
  private JLabel myProgressMessage;

  public DesignerEditorPanel(@NotNull Module module, @NotNull VirtualFile file) {
    myModule = module;
    myFile = file;

    setLayout(myLayout);
    createDesignerCard();
    createErrorCard();
    createProgressPanel();

    myToolProvider.loadDefaultTool();
  }

  private void createDesignerCard() {
    JPanel content = new JPanel(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.VERTICAL;

    myVerticalCaption = new CaptionPanel(this, false);
    content.add(myVerticalCaption, gbc);

    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    myHorizontalCaption = new CaptionPanel(this, true);
    content.add(myHorizontalCaption, gbc);

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
      public void showSelection(boolean value) {
        myDecorationLayer.showSelection(value);
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

    myPaletteListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (DesignerToolWindowManager.getInstance(getProject()).getActiveDesigner() == DesignerEditorPanel.this) {
          Item paletteItem = (Item)PaletteManager.getInstance(getProject()).getActiveItem();
          if (paletteItem != null) {
            myToolProvider.setActiveTool(new CreationTool(true, createCreationFactory(paletteItem)));
          }
          else if (myToolProvider.getActiveTool() instanceof CreationTool) {
            myToolProvider.loadDefaultTool();
          }
        }
      }
    };

    myToolProvider = new ToolProvider() {
      @Override
      public void loadDefaultTool() {
        setActiveTool(new SelectionTool());
      }

      @Override
      public void setActiveTool(InputTool tool) {
        if (getActiveTool() instanceof CreationTool && !(tool instanceof CreationTool)) {
          PaletteManager.getInstance(getProject()).clearActiveItem();
        }
        super.setActiveTool(tool);
      }

      @Override
      public boolean execute(final ThrowableRunnable<Exception> operation, String command, final boolean updateProperties) {
        final boolean[] is = {true};
        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          public void run() {
            is[0] = DesignerEditorPanel.this.execute(operation, updateProperties);
          }
        }, command, null);
        return is[0];
      }

      @Override
      public void execute(final List<EditOperation> operations, String command) {
        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          public void run() {
            DesignerEditorPanel.this.execute(operations);
          }
        }, command, null);
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
    gbc.fill = GridBagConstraints.BOTH;

    myScrollPane = ScrollPaneFactory.createScrollPane(myLayeredPane);
    myScrollPane.setBackground(Color.WHITE);
    content.add(myScrollPane, gbc);

    myActionPanel = new DesignerActionPanel(this, myGlassLayer);

    myDesignerCard = new JPanel(new FillLayout());
    myDesignerCard.add(myActionPanel.getToolbarComponent());
    myDesignerCard.add(content);
    add(myDesignerCard, DESIGNER_CARD);

    PaletteManager.getInstance(getProject()).addSelectionListener(myPaletteListener);
  }

  protected final void showDesignerCard() {
    myErrorMessages.removeAll();
    myErrorStack.setText(null);
    myLayeredPane.revalidate();
    myLayout.show(this, DESIGNER_CARD);
  }

  private void createErrorCard() {
    myErrorPanel = new JPanel(new BorderLayout());
    myErrorMessages = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 10, 5, true, false));
    myErrorPanel.add(myErrorMessages, BorderLayout.PAGE_START);

    myErrorStack = new JTextArea(50, 20);
    myErrorStack.setEditable(false);

    myErrorStackLayout = new CardLayout();
    myErrorStackPanel = new JPanel(myErrorStackLayout);
    myErrorStackPanel.add(new JLabel(), ERROR_NO_STACK_CARD);
    myErrorStackPanel.add(ScrollPaneFactory.createScrollPane(myErrorStack), ERROR_STACK_CARD);

    myErrorPanel.add(myErrorStackPanel, BorderLayout.CENTER);

    add(myErrorPanel, ERROR_CARD);
  }

  public final void showError(@NotNull String message, @NotNull Throwable e) {
    while (e instanceof InvocationTargetException) {
      if (e.getCause() == null) {
        break;
      }
      e = e.getCause();
    }

    ErrorInfo info = new ErrorInfo();
    info.myMessage = info.myDisplayMessage = message;
    info.myThrowable = e;
    configureError(info);

    if (info.myShowMessage) {
      showErrorPage(info);
    }
    if (info.myShowLog) {
      LOG.error(LogMessageEx.createEvent(info.myDisplayMessage,
                                         info.myMessage + "\n" + ExceptionUtil.getThrowableText(info.myThrowable),
                                         new Attachment(myFile)));
    }
    else {
      LOG.info(info.myDisplayMessage + "\n" + info.myMessage, info.myThrowable);
    }
  }

  protected abstract void configureError(final @NotNull ErrorInfo info);

  protected void showErrorPage(final ErrorInfo info) {
    storeState();
    hideProgress();
    myRootComponent = null;

    myErrorMessages.removeAll();

    if (info.myShowStack) {
      info.myMessages.add(0, new FixableMessageInfo(true, info.myDisplayMessage, "", "", null, null));

      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      info.myThrowable.printStackTrace(new PrintStream(stream));
      myErrorStack.setText(stream.toString());
      myErrorStackLayout.show(myErrorStackPanel, ERROR_STACK_CARD);
    }
    else {
      myErrorStack.setText(null);
      myErrorStackLayout.show(myErrorStackPanel, ERROR_NO_STACK_CARD);
    }

    for (FixableMessageInfo message : info.myMessages) {
      addErrorMessage(message, message.myErrorIcon ? Messages.getErrorIcon() : Messages.getWarningIcon());
    }

    myErrorPanel.revalidate();
    myLayout.show(this, ERROR_CARD);

    DesignerToolWindowManager.getInstance(getProject()).refresh(true);
    repaint();
  }

  private void addErrorMessage(final FixableMessageInfo message, Icon icon) {
    if (message.myLinkText.length() > 0 || message.myAfterLinkText.length() > 0) {
      HyperlinkLabel warnLabel = new HyperlinkLabel();
      warnLabel.setOpaque(false);
      warnLabel.setHyperlinkText(message.myBeforeLinkText, message.myLinkText, message.myAfterLinkText);
      warnLabel.setIcon(icon);

      if (message.myQuickFix != null) {
        warnLabel.addHyperlinkListener(new HyperlinkListener() {
          public void hyperlinkUpdate(final HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              message.myQuickFix.run();
            }
          }
        });
      }
      myErrorMessages.add(warnLabel);
    }
    else {
      JBLabel warnLabel = new JBLabel();
      warnLabel.setOpaque(false);
      warnLabel.setText("<html><body>" + message.myBeforeLinkText.replace("\n", "<br>") + "</body></html>");
      warnLabel.setIcon(icon);
      myErrorMessages.add(warnLabel);
    }
    if (message.myAdditionalFixes != null && message.myAdditionalFixes.size() > 0) {
      JPanel fixesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      fixesPanel.setBorder(IdeBorderFactory.createEmptyBorder(3, 0, 10, 0));
      fixesPanel.setOpaque(false);
      fixesPanel.add(Box.createHorizontalStrut(icon.getIconWidth()));

      for (Pair<String, Runnable> pair : message.myAdditionalFixes) {
        HyperlinkLabel fixLabel = new HyperlinkLabel();
        fixLabel.setOpaque(false);
        fixLabel.setHyperlinkText(pair.getFirst());
        final Runnable fix = pair.getSecond();

        fixLabel.addHyperlinkListener(new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              fix.run();
            }
          }
        });
        fixesPanel.add(fixLabel);
      }
      myErrorMessages.add(fixesPanel);
    }
  }

  private void createProgressPanel() {
    myProgressIcon = new AsyncProcessIcon("Designer progress");
    myProgressMessage = new JLabel();

    JPanel progressBlock = new JPanel();
    progressBlock.add(myProgressIcon);
    progressBlock.add(myProgressMessage);
    progressBlock.setBorder(IdeBorderFactory.createRoundedBorder());

    myProgressPanel = new JPanel(new GridBagLayout());
    myProgressPanel.add(progressBlock,
                        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0),
                                               0, 0));
    myProgressPanel.setOpaque(false);
  }

  protected final void showProgress(String message) {
    myProgressMessage.setText(message);
    if (myProgressPanel.getParent() == null) {
      myGlassLayer.setEnabled(false);
      myProgressIcon.resume();
      myLayeredPane.add(myProgressPanel, LAYER_PROGRESS);
      myLayeredPane.repaint();
    }
  }

  protected final void hideProgress() {
    myGlassLayer.setEnabled(true);
    myProgressIcon.suspend();
    myLayeredPane.remove(myProgressPanel);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @NotNull
  public Module getModule() {
    return myModule;
  }

  public Project getProject() {
    return myModule.getProject();
  }

  public EditableArea getSurfaceArea() {
    return mySurfaceArea;
  }

  public EditableArea getActionsArea() {
    TreeEditableArea treeArea = DesignerToolWindowManager.getInstance(getProject()).getTreeArea();
    return treeArea == null ? mySurfaceArea : treeArea;
  }

  public ToolProvider getToolProvider() {
    return myToolProvider;
  }

  public DesignerActionPanel getActionPanel() {
    return myActionPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myDesignerCard.isVisible() ? myGlassLayer : myErrorPanel;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // State
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  public List<?> getExpandedComponents() {
    return myExpandedComponents;
  }

  public void setExpandedComponents(@Nullable List<?> expandedComponents) {
    myExpandedComponents = expandedComponents;
  }

  public Property getSelectionProperty() {
    return mySelectionProperty;
  }

  public void setSelectionProperty(Property selectionProperty) {
    mySelectionProperty = selectionProperty;
  }

  protected void storeState() {
    if (myRootComponent != null && myExpandedState == null && mySelectionState == null) {
      myExpandedState = new int[myExpandedComponents == null ? 0 : myExpandedComponents.size()][];
      for (int i = 0; i < myExpandedState.length; i++) {
        IntArrayList path = new IntArrayList();
        componentToPath((RadComponent)myExpandedComponents.get(i), path);
        myExpandedState[i] = path.toArray();
      }

      List<RadComponent> selection = mySurfaceArea.getSelection();
      mySelectionState = new int[selection.size()][];
      for (int i = 0; i < mySelectionState.length; i++) {
        IntArrayList path = new IntArrayList();
        componentToPath(selection.get(i), path);
        mySelectionState[i] = path.toArray();
      }

      myExpandedComponents = null;
      myToolProvider.loadDefaultTool();
      mySurfaceArea.deselectAll();
    }
  }

  private static void componentToPath(RadComponent component, IntArrayList path) {
    RadComponent parent = component.getParent();

    if (parent != null) {
      path.add(0, parent.getChildren().indexOf(component));
      componentToPath(parent, path);
    }
  }

  protected void restoreState() {
    DesignerToolWindowManager toolManager = DesignerToolWindowManager.getInstance(getProject());

    if (myExpandedState == null || mySelectionProperty == null || myRootComponent == null) {
      toolManager.refresh(true);
    }
    else {
      List<RadComponent> expanded = new ArrayList<RadComponent>();
      for (int[] path : myExpandedState) {
        pathToComponent(expanded, myRootComponent, path, 0);
      }
      myExpandedComponents = expanded;
      toolManager.expandFromState();

      List<RadComponent> selection = new ArrayList<RadComponent>();
      for (int[] path : mySelectionState) {
        pathToComponent(selection, myRootComponent, path, 0);
      }
      mySurfaceArea.setSelection(selection);
    }

    myExpandedState = null;
    mySelectionState = null;
  }

  private static void pathToComponent(List<RadComponent> components, RadComponent component, int[] path, int index) {
    if (index == path.length) {
      components.add(component);
    }
    else {
      List<RadComponent> children = component.getChildren();
      int componentIndex = path[index];
      if (0 <= componentIndex && componentIndex < children.size()) {
        pathToComponent(components, children.get(componentIndex), path, index + 1);
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public abstract String getPlatformTarget();

  public void updateTreeArea(EditableArea area) {
  }

  protected abstract ComponentDecorator getRootSelectionDecorator();

  @Nullable
  protected abstract EditOperation processRootOperation(OperationContext context);

  protected abstract boolean execute(ThrowableRunnable<Exception> operation, boolean updateProperties);

  protected abstract void execute(List<EditOperation> operations);

  @NotNull
  protected abstract ComponentCreationFactory createCreationFactory(Item paletteItem);

  @Nullable
  public abstract ComponentPasteFactory createPasteFactory(String xmlComponents);

  public void activate() {
  }

  public void deactivate() {
  }

  @NotNull
  public DesignerEditorState createState() {
    return new DesignerEditorState(myFile);
  }

  public boolean isEditorValid() {
    return myFile.isValid();
  }

  @Override
  public Object getData(@NonNls String dataId) {
    // TODO: support keys
    return myActionPanel.getData(dataId);
  }

  public void dispose() {
    PaletteManager.getInstance(getProject()).removeSelectionListener(myPaletteListener);
    Disposer.dispose(myProgressIcon);
  }

  public RadComponent getRootComponent() {
    return myRootComponent;
  }

  public Object[] getTreeRoots() {
    return myRootComponent == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : new Object[]{myRootComponent};
  }

  public abstract TreeComponentDecorator getTreeDecorator();

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static final class FillLayout implements LayoutManager2 {
    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
      return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
      return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
      return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Component toolbar = parent.getComponent(0);
      Dimension toolbarSize = toolbar.isVisible() ? toolbar.getPreferredSize() : new Dimension();
      Dimension contentSize = parent.getComponent(1).getPreferredSize();
      return new Dimension(Math.max(toolbarSize.width, contentSize.width), toolbarSize.height + contentSize.height);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      Component toolbar = parent.getComponent(0);
      Dimension toolbarSize = toolbar.isVisible() ? toolbar.getMinimumSize() : new Dimension();
      Dimension contentSize = parent.getComponent(1).getMinimumSize();
      return new Dimension(Math.max(toolbarSize.width, contentSize.width), toolbarSize.height + contentSize.height);
    }

    @Override
    public void layoutContainer(Container parent) {
      int width = parent.getWidth();
      int height = parent.getHeight();
      Component toolbar = parent.getComponent(0);
      Dimension toolbarSize = toolbar.isVisible() ? toolbar.getPreferredSize() : new Dimension();
      toolbar.setBounds(0, 0, width, toolbarSize.height);
      parent.getComponent(1).setBounds(0, toolbarSize.height, width, height - toolbarSize.height);
    }
  }

  private final class MyLayeredPane extends JLayeredPane implements Scrollable {
    public void doLayout() {
      for (int i = getComponentCount() - 1; i >= 0; i--) {
        Component component = getComponent(i);
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

  public static final class ErrorInfo {
    public String myMessage;
    public String myDisplayMessage;

    public final List<FixableMessageInfo> myMessages = new ArrayList<FixableMessageInfo>();

    public Throwable myThrowable;

    public boolean myShowMessage = true;
    public boolean myShowStack = true;
    public boolean myShowLog;
  }

  public static final class FixableMessageInfo {
    public final boolean myErrorIcon;
    public final String myBeforeLinkText;
    public final String myLinkText;
    public final String myAfterLinkText;
    public final Runnable myQuickFix;
    public final List<Pair<String, Runnable>> myAdditionalFixes;

    public FixableMessageInfo(boolean errorIcon,
                              String beforeLinkText,
                              String linkText,
                              String afterLinkText,
                              Runnable quickFix,
                              List<Pair<String, Runnable>> additionalFixes) {
      myErrorIcon = errorIcon;
      myBeforeLinkText = beforeLinkText;
      myLinkText = linkText;
      myAfterLinkText = afterLinkText;
      myQuickFix = quickFix;
      myAdditionalFixes = additionalFixes;
    }
  }
}