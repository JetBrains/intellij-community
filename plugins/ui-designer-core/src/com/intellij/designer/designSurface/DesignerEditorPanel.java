/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.designer.*;
import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.designer.actions.CommonEditActionsProvider;
import com.intellij.designer.actions.DesignerActionPanel;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.componentTree.TreeEditableArea;
import com.intellij.designer.designSurface.tools.*;
import com.intellij.designer.model.*;
import com.intellij.designer.palette.PaletteGroup;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.designer.palette.PaletteToolWindowManager;
import com.intellij.designer.propertyTable.InplaceContext;
import com.intellij.designer.propertyTable.PropertyTableTab;
import com.intellij.designer.propertyTable.TablePanelActionPolicy;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.FixedHashMap;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public abstract class DesignerEditorPanel extends JPanel
  implements DesignerEditorPanelFacade, DataProvider, ModuleProvider, RadPropertyContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.designer.designSurface.DesignerEditorPanel");

  protected static final Integer LAYER_COMPONENT = JLayeredPane.DEFAULT_LAYER;
  protected static final Integer LAYER_DECORATION = JLayeredPane.POPUP_LAYER;
  protected static final Integer LAYER_FEEDBACK = JLayeredPane.DRAG_LAYER;
  protected static final Integer LAYER_GLASS = LAYER_FEEDBACK + 100;
  protected static final Integer LAYER_INPLACE_EDITING = LAYER_GLASS + 100;
  private static final Integer LAYER_PROGRESS = LAYER_INPLACE_EDITING + 100;

  private final static String DESIGNER_CARD = "designer";
  private final static String ERROR_CARD = "error";
  private final static String ERROR_STACK_CARD = "stack";
  private final static String ERROR_NO_STACK_CARD = "no_stack";

  @NotNull
  private final DesignerEditor myEditor;
  private final Project myProject;
  private Module myModule;
  protected final VirtualFile myFile;

  private final CardLayout myLayout = new CardLayout();
  private final ThreeComponentsSplitter myContentSplitter = new ThreeComponentsSplitter();
  private final JPanel myPanel = new JPanel(myLayout);
  private JComponent myDesignerCard;

  protected DesignerActionPanel myActionPanel;

  protected CaptionPanel myHorizontalCaption;
  protected CaptionPanel myVerticalCaption;

  protected JScrollPane myScrollPane;
  protected JLayeredPane myLayeredPane;
  protected GlassLayer myGlassLayer;
  private DecorationLayer myDecorationLayer;
  private FeedbackLayer myFeedbackLayer;
  private InplaceEditingLayer myInplaceEditingLayer;

  protected ToolProvider myToolProvider;
  protected EditableArea mySurfaceArea;

  protected RadComponent myRootComponent;

  protected QuickFixManager myQuickFixManager;

  private PaletteItem myActivePaletteItem;
  private List<?> myExpandedComponents;
  private final Map<String, Property> mySelectionPropertyMap = new HashMap<>();
  private int[][] myExpandedState;
  private int[][] mySelectionState;
  private final Map<String, int[][]> mySourceSelectionState = new FixedHashMap<>(16);

  private FixableMessageAction myWarnAction;

  private JPanel myErrorPanel;
  protected JPanel myErrorMessages;
  private JPanel myErrorStackPanel;
  private CardLayout myErrorStackLayout;
  private JTextArea myErrorStack;

  private JPanel myProgressPanel;
  private AsyncProcessIcon myProgressIcon;
  private JLabel myProgressMessage;

  protected String myLastExecuteCommand;

  public DesignerEditorPanel(@NotNull DesignerEditor editor, @NotNull Project project, Module module, @NotNull VirtualFile file) {
    myEditor = editor;
    myProject = project;
    myModule = module;
    myFile = file;

    initUI();

    myToolProvider.loadDefaultTool();
  }

  private void initUI() {
    setLayout(new BorderLayout());

    myContentSplitter.setDividerWidth(0);
    myContentSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    add(myContentSplitter, BorderLayout.CENTER);

    createDesignerCard();
    createErrorCard();
    createProgressPanel();

    UIUtil.invokeLaterIfNeeded(() -> {
      DesignerEditorPanel designer = this;
      getDesignerWindowManager().bind(designer);
      getPaletteWindowManager().bind(designer);
    });
  }

  private void createDesignerCard() {
    JPanel panel = new JPanel(new LightFillLayout());
    myContentSplitter.setInnerComponent(panel);

    myLayeredPane = new MyLayeredPane();

    mySurfaceArea = createEditableArea();

    myToolProvider = createToolProvider();

    myGlassLayer = createGlassLayer(myToolProvider, mySurfaceArea);
    myLayeredPane.add(myGlassLayer, LAYER_GLASS);

    myDecorationLayer = createDecorationLayer();
    myLayeredPane.add(myDecorationLayer, LAYER_DECORATION);

    myFeedbackLayer = createFeedbackLayer();
    myLayeredPane.add(myFeedbackLayer, LAYER_FEEDBACK);

    myInplaceEditingLayer = createInplaceEditingLayer();
    myLayeredPane.add(myInplaceEditingLayer, LAYER_INPLACE_EDITING);

    myScrollPane = createScrollPane(myLayeredPane);

    myQuickFixManager = new QuickFixManager(this, myGlassLayer, myScrollPane.getViewport());

    myActionPanel = createActionPanel();
    myWarnAction = new FixableMessageAction();

    panel.add(myActionPanel.getToolbarComponent());
    panel.add(myPanel);

    myDesignerCard = createDesignerCardPanel();
    myPanel.add(myDesignerCard, DESIGNER_CARD);

    mySurfaceArea.addSelectionListener(new ComponentSelectionListener() {
      @Override
      public void selectionChanged(EditableArea area) {
        storeSourceSelectionState();
      }
    });
  }

  protected JComponent createDesignerCardPanel() {
    JPanel content = new JPanel(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.VERTICAL;

    myVerticalCaption = createCaptionPanel(false);
    content.add(myVerticalCaption, gbc);

    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    myHorizontalCaption = createCaptionPanel(true);
    content.add(myHorizontalCaption, gbc);

    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.fill = GridBagConstraints.BOTH;

    content.add(myScrollPane, gbc);

    myHorizontalCaption.attachToScrollPane(myScrollPane);
    myVerticalCaption.attachToScrollPane(myScrollPane);

    return content;
  }

  public final ThreeComponentsSplitter getContentSplitter() {
    return myContentSplitter;
  }

  protected EditableArea createEditableArea() {
    return new DesignerEditableArea();
  }

  protected ToolProvider createToolProvider() {
    return new DesignerToolProvider();
  }

  protected GlassLayer createGlassLayer(ToolProvider provider, EditableArea area) {
    return new GlassLayer(provider, area);
  }

  protected DecorationLayer createDecorationLayer() {
    return new DecorationLayer(this, mySurfaceArea);
  }

  protected FeedbackLayer createFeedbackLayer() {
    return new FeedbackLayer(this);
  }

  protected InplaceEditingLayer createInplaceEditingLayer() {
    return new InplaceEditingLayer(this);
  }

  protected CaptionPanel createCaptionPanel(boolean horizontal) {
    return new CaptionPanel(this, horizontal, true);
  }

  protected JScrollPane createScrollPane(@NotNull JLayeredPane content) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(content);
    scrollPane.setBackground(new JBColor(Color.WHITE, UIUtil.getListBackground()));
    return scrollPane;
  }

  protected DesignerActionPanel createActionPanel() {
    return new DesignerActionPanel(this, myGlassLayer);
  }

  @Nullable
  public final PaletteItem getActivePaletteItem() {
    return myActivePaletteItem;
  }

  public final void activatePaletteItem(@Nullable PaletteItem paletteItem) {
    myActivePaletteItem = paletteItem;
    if (paletteItem != null) {
      myToolProvider.setActiveTool(new CreationTool(true, createCreationFactory(paletteItem)));
    }
    else if (myToolProvider.getActiveTool() instanceof CreationTool) {
      myToolProvider.loadDefaultTool();
    }
  }

  protected final void showDesignerCard() {
    myErrorMessages.removeAll();
    myErrorStack.setText(null);
    myLayeredPane.revalidate();

    if (myHorizontalCaption != null) {
      myHorizontalCaption.update();
    }
    if (myVerticalCaption != null) {
      myVerticalCaption.update();
    }

    myLayout.show(myPanel, DESIGNER_CARD);
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

    myPanel.add(myErrorPanel, ERROR_CARD);
  }

  public final void showError(@NotNull String message, @NotNull Throwable e) {
    if (isProjectClosed()) {
      return;
    }

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
                                         getErrorAttachments(info)));
    }
  }

  protected Attachment[] getErrorAttachments(ErrorInfo info) {
    return new Attachment[]{AttachmentFactory.createAttachment(myFile)};
  }

  protected abstract void configureError(@NotNull ErrorInfo info);

  protected void showErrorPage(ErrorInfo info) {
    storeState();
    hideProgress();
    myRootComponent = null;

    myErrorMessages.removeAll();

    if (info.myShowStack) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      info.myThrowable.printStackTrace(new PrintStream(stream));
      myErrorStack.setText(stream.toString());
      myErrorStackLayout.show(myErrorStackPanel, ERROR_STACK_CARD);
    }
    else {
      myErrorStack.setText(null);
      myErrorStackLayout.show(myErrorStackPanel, ERROR_NO_STACK_CARD);
    }

    addErrorMessage(new FixableMessageInfo(true, info.myDisplayMessage, "", "", null, null), Messages.getErrorIcon());
    for (FixableMessageInfo message : info.myMessages) {
      addErrorMessage(message, message.myErrorIcon ? Messages.getErrorIcon() : Messages.getWarningIcon());
    }

    myErrorPanel.revalidate();
    myLayout.show(myPanel, ERROR_CARD);

    getDesignerToolWindow().refresh(true);
    repaint();
  }

  protected void addErrorMessage(final FixableMessageInfo message, Icon icon) {
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

  protected final void showWarnMessages(@Nullable List<FixableMessageInfo> messages) {
    if (messages == null) {
      myWarnAction.hide();
    }
    else {
      myWarnAction.show(messages);
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
                                               0, 0)
    );
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
  @Override
  public final Module getModule() {
    if (myModule.isDisposed()) {
      myModule = findModule(myProject, myFile);
      if (myModule == null) {
        throw new IllegalArgumentException("No module for file " + myFile + " in project " + myProject);
      }
    }
    return myModule;
  }

  @Nullable
  protected Module findModule(Project project, VirtualFile file) {
    return ModuleUtilCore.findModuleForFile(file, project);
  }


  @NotNull
  public final DesignerEditor getEditor() {
    return myEditor;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  public final Project getProject() {
    return myProject;
  }

  public final boolean isProjectClosed() {
    return myProject.isDisposed() || !myProject.isOpen();
  }

  public EditableArea getSurfaceArea() {
    return mySurfaceArea;
  }

  public ToolProvider getToolProvider() {
    return myToolProvider;
  }

  public DesignerActionPanel getActionPanel() {
    return myActionPanel;
  }

  public InplaceEditingLayer getInplaceEditingLayer() {
    return myInplaceEditingLayer;
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

  public Property getSelectionProperty(@Nullable String key) {
    return mySelectionPropertyMap.get(key);
  }

  public void setSelectionProperty(@Nullable String key, Property selectionProperty) {
    mySelectionPropertyMap.put(key, selectionProperty);
  }

  protected void storeState() {
    if (myRootComponent != null && myExpandedState == null && mySelectionState == null) {
      myExpandedState = new int[myExpandedComponents == null ? 0 : myExpandedComponents.size()][];
      for (int i = 0; i < myExpandedState.length; i++) {
        IntArrayList path = new IntArrayList();
        componentToPath((RadComponent)myExpandedComponents.get(i), path);
        myExpandedState[i] = path.toArray();
      }

      mySelectionState = getSelectionState();

      myExpandedComponents = null;

      InputTool tool = myToolProvider.getActiveTool();
      if (!(tool instanceof MarqueeTracker) &&
          !(tool instanceof CreationTool) &&
          !(tool instanceof PasteTool)) {
        myToolProvider.loadDefaultTool();
      }
    }
  }

  private void storeSourceSelectionState() {
    if (!CommonEditActionsProvider.isDeleting) {
      mySourceSelectionState.put(getEditorText(), getSelectionState());
    }
  }

  private int[][] getSelectionState() {
    return getSelectionState(mySurfaceArea.getSelection());
  }

  protected static int[][] getSelectionState(List<RadComponent> selection) {
    int[][] selectionState = new int[selection.size()][];

    for (int i = 0; i < selectionState.length; i++) {
      IntArrayList path = new IntArrayList();
      componentToPath(selection.get(i), path);
      selectionState[i] = path.toArray();
    }

    return selectionState;
  }

  private static void componentToPath(RadComponent component, IntArrayList path) {
    RadComponent parent = component.getParent();

    if (parent != null) {
      path.add(0, parent.getChildren().indexOf(component));
      componentToPath(parent, path);
    }
  }

  protected void restoreState() {
    DesignerToolWindowContent toolManager = getDesignerToolWindow();

    if (myExpandedState != null) {
      List<RadComponent> expanded = new ArrayList<>();
      for (int[] path : myExpandedState) {
        pathToComponent(expanded, myRootComponent, path, 0);
      }
      myExpandedComponents = expanded;
      toolManager.expandFromState();
      myExpandedState = null;
    }

    List<RadComponent> selection = new ArrayList<>();

    int[][] selectionState = mySourceSelectionState.get(getEditorText());
    if (selectionState != null) {
      for (int[] path : selectionState) {
        pathToComponent(selection, myRootComponent, path, 0);
      }
    }

    if (selection.isEmpty()) {
      if (mySelectionState != null) {
        for (int[] path : mySelectionState) {
          pathToComponent(selection, myRootComponent, path, 0);
        }
      }
    }

    if (selection.isEmpty()) {
      toolManager.refresh(true);
    }
    else {
      mySurfaceArea.setSelection(selection);
    }

    mySelectionState = null;
  }

  protected static void pathToComponent(List<RadComponent> components, RadComponent component, int[] path, int index) {
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

  protected RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
    if (myRootComponent != null) {
      FindComponentVisitor visitor = new FindComponentVisitor(myLayeredPane, filter, x, y);
      myRootComponent.accept(visitor, false);
      return visitor.getResult();
    }
    return null;
  }

  protected abstract ComponentDecorator getRootSelectionDecorator();

  @Nullable
  protected EditOperation processRootOperation(OperationContext context) {
    return null;
  }

  protected abstract boolean execute(ThrowableRunnable<Exception> operation, boolean updateProperties);

  protected abstract void executeWithReparse(ThrowableRunnable<Exception> operation);

  protected abstract void execute(List<EditOperation> operations);

  public abstract List<PaletteGroup> getPaletteGroups();

  /**
   * Returns a suitable version label from the version attribute from a {@link PaletteItem} version
   */
  @NotNull
  public String getVersionLabel(@Nullable String version) {
    return StringUtil.notNullize(version);
  }

  public boolean isDeprecated(@Nullable String deprecatedIn) {
    return !StringUtil.isEmpty(deprecatedIn);
  }

  protected InputTool createDefaultTool() {
    return new SelectionTool();
  }

  @NotNull
  protected abstract ComponentCreationFactory createCreationFactory(PaletteItem paletteItem);

  @Nullable
  public abstract ComponentPasteFactory createPasteFactory(String xmlComponents);

  public abstract String getEditorText();

  public void activate() {
  }

  public void deactivate() {
  }

  @NotNull
  public DesignerEditorState createState() {
    return new DesignerEditorState(myFile, getZoom());
  }

  public boolean isEditorValid() {
    return myFile.isValid();
  }

  @Override
  public Object getData(@NonNls String dataId) {
    return myActionPanel.getData(dataId);
  }

  public void dispose() {
    Disposer.dispose(myProgressIcon);
    getDesignerWindowManager().dispose(this);
    getPaletteWindowManager().dispose(this);
    Disposer.dispose(myContentSplitter);
  }

  protected AbstractToolWindowManager getDesignerWindowManager() {
    return DesignerToolWindowManager.getInstance(myProject);
  }

  protected AbstractToolWindowManager getPaletteWindowManager() {
    return PaletteToolWindowManager.getInstance(myProject);
  }

  public DesignerToolWindowContent getDesignerToolWindow() {
    return DesignerToolWindowManager.getInstance(this);
  }

  protected PaletteToolWindowContent getPaletteToolWindow() {
    return PaletteToolWindowManager.getInstance(this);
  }

  @Nullable
  public WrapInProvider getWrapInProvider() {
    return null;
  }

  @Nullable
  public RadComponent getRootComponent() {
    return myRootComponent;
  }

  public Object[] getTreeRoots() {
    return myRootComponent == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : new Object[]{myRootComponent};
  }

  public abstract TreeComponentDecorator getTreeDecorator();

  public void handleTreeArea(TreeEditableArea treeArea) {
  }

  @NotNull
  public TablePanelActionPolicy getTablePanelActionPolicy() {
    return TablePanelActionPolicy.ALL;
  }

  @Nullable
  public PropertyTableTab[] getPropertyTableTabs() {
    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Zooming
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public boolean isZoomSupported() {
    return false;
  }

  public void zoom(@NotNull ZoomType type) {
  }

  public void setZoom(double zoom) {
  }

  public double getZoom() {
    return 1;
  }

  protected void viewZoomed() {
    // Hide quickfix light bulbs; position can be obsolete after the zoom level has changed
    myQuickFixManager.hideHint();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Inspection
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public void loadInspections(ProgressIndicator progress) {
  }

  public void updateInspections() {
    myQuickFixManager.update();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Size of the scene, in scroll pane view port pixels.
   */
  @NotNull
  protected Dimension getSceneSize(Component target) {
    int width = 0;
    int height = 0;

    if (myRootComponent != null) {
      Rectangle bounds = myRootComponent.getBounds(target);
      width = Math.max(width, (int)bounds.getMaxX());
      height = Math.max(height, (int)bounds.getMaxY());

      for (RadComponent component : myRootComponent.getChildren()) {
        Rectangle childBounds = component.getBounds(target);
        width = Math.max(width, (int)childBounds.getMaxX());
        height = Math.max(height, (int)childBounds.getMaxY());
      }
    }

    width += 50;
    height += 40;

    return new Dimension(width, height);
  }

  protected class DesignerEditableArea extends ComponentEditableArea {
    public DesignerEditableArea() {
      super(myLayeredPane);
    }

    @Override
    protected void fireSelectionChanged() {
      super.fireSelectionChanged();
      myLayeredPane.revalidate();
      myLayeredPane.repaint();
    }

    @Override
    public void scrollToSelection() {
      List<RadComponent> selection = getSelection();
      if (selection.size() == 1) {
        Rectangle bounds = selection.get(0).getBounds(myLayeredPane);
        if (bounds != null) {
          myLayeredPane.scrollRectToVisible(bounds);
        }
      }
    }

    @Override
    public RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
      return DesignerEditorPanel.this.findTarget(x, y, filter);
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

    @Override
    public ActionGroup getPopupActions() {
      return myActionPanel.getPopupActions(this);
    }

    @Override
    public String getPopupPlace() {
      return ActionPlaces.GUI_DESIGNER_EDITOR_POPUP;
    }
  }

  protected class DesignerToolProvider extends ToolProvider {
    @Override
    public void loadDefaultTool() {
      setActiveTool(createDefaultTool());
    }

    @Override
    public void setActiveTool(InputTool tool) {
      if (getActiveTool() instanceof CreationTool && !(tool instanceof CreationTool)) {
        getPaletteToolWindow().clearActiveItem();
      }
      if (!(tool instanceof SelectionTool)) {
        hideInspections();
      }
      super.setActiveTool(tool);
    }

    @Override
    public boolean execute(final ThrowableRunnable<Exception> operation, String command, final boolean updateProperties) {
      myLastExecuteCommand = command;
      final Ref<Boolean> result = Ref.create(Boolean.TRUE);
      CommandProcessor.getInstance().executeCommand(getProject(),
                                                    () -> result.set(DesignerEditorPanel.this.execute(operation, updateProperties)), command, null);
      return result.get();
    }

    @Override
    public void executeWithReparse(final ThrowableRunnable<Exception> operation, String command) {
      myLastExecuteCommand = command;
      CommandProcessor.getInstance().executeCommand(getProject(), () -> DesignerEditorPanel.this.executeWithReparse(operation), command, null);
    }

    @Override
    public void execute(final List<EditOperation> operations, String command) {
      myLastExecuteCommand = command;
      CommandProcessor.getInstance().executeCommand(getProject(), () -> DesignerEditorPanel.this.execute(operations), command, null);
    }

    @Override
    public void startInplaceEditing(@Nullable InplaceContext inplaceContext) {
      myInplaceEditingLayer.startEditing(inplaceContext);
    }

    @Override
    public void hideInspections() {
      myQuickFixManager.hideHint();
    }

    @Override
    public void showError(@NonNls String message, Throwable e) {
      DesignerEditorPanel.this.showError(message, e);
    }

    @Override
    public boolean isZoomSupported() {
      return DesignerEditorPanel.this.isZoomSupported();
    }

    @Override
    public void zoom(@NotNull ZoomType type) {
      DesignerEditorPanel.this.zoom(type);
    }

    @Override
    public void setZoom(double zoom) {
      DesignerEditorPanel.this.setZoom(zoom);
    }

    @Override
    public double getZoom() {
      return DesignerEditorPanel.this.getZoom();
    }
  }

  private final class MyLayeredPane extends JBLayeredPane implements Scrollable {
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
      Rectangle bounds = myScrollPane.getViewport().getBounds();
      Dimension size = getSceneSize(this);

      size.width = Math.max(size.width, bounds.width);
      size.height = Math.max(size.height, bounds.height);

      return size;
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

  private class FixableMessageAction extends AbstractComboBoxAction<FixableMessageInfo> {
    private final DefaultActionGroup myActionGroup = new DefaultActionGroup();
    private String myTitle;
    private boolean myIsAdded;

    public FixableMessageAction() {
      myActionPanel.getActionGroup().add(myActionGroup);

      Presentation presentation = getTemplatePresentation();
      presentation.setDescription("Warnings");
      presentation.setIcon(AllIcons.Ide.Warning_notifications);
    }

    public void show(List<FixableMessageInfo> messages) {
      if (!myIsAdded) {
        myTitle = Integer.toString(messages.size());
        setItems(messages, null);
        myActionGroup.add(this);
        myActionPanel.update();
        myIsAdded = true;
      }
    }

    public void hide() {
      if (myIsAdded) {
        myActionGroup.remove(this);
        myActionPanel.update();
        myIsAdded = false;
      }
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      DefaultActionGroup actionGroup = new DefaultActionGroup();
      for (final FixableMessageInfo message : myItems) {
        AnAction action;
        if ((message.myQuickFix != null && (message.myLinkText.length() > 0 || message.myAfterLinkText.length() > 0)) ||
            (message.myAdditionalFixes != null && message.myAdditionalFixes.size() > 0)) {
          final AnAction[] defaultAction = new AnAction[1];
          DefaultActionGroup popupGroup = new DefaultActionGroup() {
            @Override
            public boolean canBePerformed(DataContext context) {
              return true;
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
              defaultAction[0].actionPerformed(e);
            }
          };
          popupGroup.setPopup(true);
          action = popupGroup;

          if (message.myQuickFix != null && (message.myLinkText.length() > 0 || message.myAfterLinkText.length() > 0)) {
            AnAction popupAction = new AnAction() {
              @Override
              public void actionPerformed(AnActionEvent e) {
                message.myQuickFix.run();
              }
            };
            popupAction.getTemplatePresentation().setText(cleanText(message.myLinkText + message.myAfterLinkText));
            popupGroup.add(popupAction);
            defaultAction[0] = popupAction;
          }
          if (message.myAdditionalFixes != null && message.myAdditionalFixes.size() > 0) {
            for (final Pair<String, Runnable> pair : message.myAdditionalFixes) {
              AnAction popupAction = new AnAction() {
                @Override
                public void actionPerformed(AnActionEvent e) {
                  pair.second.run();
                }
              };
              popupAction.getTemplatePresentation().setText(cleanText(pair.first));
              popupGroup.add(popupAction);
              if (defaultAction[0] == null) {
                defaultAction[0] = popupAction;
              }
            }
          }
        }
        else {
          action = new EmptyAction(true);
        }
        actionGroup.add(action);
        update(message, action.getTemplatePresentation(), true);
      }
      return actionGroup;
    }

    @Override
    protected void update(FixableMessageInfo item, Presentation presentation, boolean popup) {
      if (popup) {
        presentation.setText(cleanText(item.myBeforeLinkText));
      }
      else {
        presentation.setText(myTitle);
      }
    }

    private String cleanText(String text) {
      if (text != null) {
        text = text.trim();
        text = StringUtil.replace(text, "&nbsp;", " ");
        text = StringUtil.replace(text, "\n", " ");

        StringBuilder builder = new StringBuilder();
        int length = text.length();
        boolean whitespace = false;

        for (int i = 0; i < length; i++) {
          char ch = text.charAt(i);
          if (ch == ' ') {
            if (!whitespace) {
              whitespace = true;
              builder.append(ch);
            }
          }
          else {
            whitespace = false;
            builder.append(ch);
          }
        }

        text = builder.toString();
      }
      return text;
    }

    @Override
    protected boolean selectionChanged(FixableMessageInfo item) {
      return false;
    }
  }

  public static final class ErrorInfo {
    public String myMessage;
    public String myDisplayMessage;

    public final List<FixableMessageInfo> myMessages = new ArrayList<>();

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