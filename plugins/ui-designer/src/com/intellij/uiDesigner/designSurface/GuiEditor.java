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
package com.intellij.uiDesigner.designSurface;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightFillLayout;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.ide.palette.impl.PaletteToolWindowManager;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.componentTree.ComponentPtr;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindow;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.PropertyInspector;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.radComponents.RadTabbedPane;
import com.intellij.util.Alarm;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * <code>GuiEditor</code> is a panel with border layout. It has palette at the north,
 * tree of component with property editor at the west and editor area at the center.
 * This editor area contains internal component where user edit the UI.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GuiEditor extends JPanel implements DesignerEditorPanelFacade, DataProvider, ModuleProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.GuiEditor");

  private final Project myProject;
  @NotNull private final UIFormEditor myEditor;
  private Module myModule;
  @NotNull private final VirtualFile myFile;

  /**
   * for debug purposes
   */
  private Exception myWhere;

  /**
   * All component are on this layer
   */
  private static final Integer LAYER_COMPONENT = JLayeredPane.DEFAULT_LAYER;
  /**
   * This layer contains all "passive" decorators such as component boundaries
   * and selection rectangle.
   */
  private static final Integer LAYER_PASSIVE_DECORATION = JLayeredPane.POPUP_LAYER;
  /**
   * We show (and move) dragged component at this layer
   */
  private static final Integer LAYER_DND = JLayeredPane.DRAG_LAYER;
  /**
   * This is the topmost layer. It gets and redispatch all incoming events
   */
  private static final Integer LAYER_GLASS = new Integer(JLayeredPane.DRAG_LAYER.intValue() + 100);
  /**
   * This layer contains all "active" decorators. This layer should be over
   * LAYER_GLASS because active decorators must get AWT events to work correctly.
   */
  private static final Integer LAYER_ACTIVE_DECORATION = new Integer(LAYER_GLASS.intValue() + 100);
  /**
   * This layer contains all inplace editors.
   */
  private static final Integer LAYER_INPLACE_EDITING = new Integer(LAYER_ACTIVE_DECORATION.intValue() + 100);

  private final EventListenerList myListenerList;
  /**
   * we have to store document here but not file because there can be a situation when
   * document we added listener to has been disposed, and remove listener will be applied to
   * a new document (got by file) -> assertion (see SCR 14143)
   */
  private final Document myDocument;

  final MainProcessor myProcessor;
  @NotNull private final JScrollPane myScrollPane;
  /**
   * This layered pane contains all layers to lay components out and to
   * show all necessary decoration items
   */
  @NotNull private final MyLayeredPane myLayeredPane;
  /**
   * The component which represents decoration layer. All passive
   * decorators are on this layer.
   */
  private final PassiveDecorationLayer myDecorationLayer;
  /**
   * The component which represents layer where located all dragged
   * components
   */
  private final DragLayer myDragLayer;
  /**
   * This layer contains all inplace editors
   */
  private final InplaceEditingLayer myInplaceEditingLayer;
  /**
   * Brings functionality to "DEL" button
   */
  private final MyDeleteProvider myDeleteProvider;
  /**
   * Rerun error analizer
   */
  private final MyPsiTreeChangeListener myPsiTreeChangeListener;

  private RadRootContainer myRootContainer;
  /**
   * Panel with components palette.
   */
  //@NotNull private final PalettePanel myPalettePanel;
  /**
   * GuiEditor should not react on own events. If <code>myInsideChange</code>
   * is <code>true</code> then we do not react on incoming DocumentEvent.
   */
  private boolean myInsideChange;
  private final DocumentAdapter myDocumentListener;
  private final CardLayout myCardLayout = new CardLayout();
  private final ThreeComponentsSplitter myContentSplitter = new ThreeComponentsSplitter();
  private final JPanel myCardPanel = new JPanel(myCardLayout);

  @NonNls private final static String CARD_VALID = "valid";
  @NonNls private final static String CARD_INVALID = "invalid";
  private final JPanel myValidCard;
  private final JPanel myInvalidCard;
  private boolean myInvalid = false;

  private final CutCopyPasteSupport myCutCopyPasteSupport;
  /**
   * Implementation of Crtl+W and Ctrl+Shift+W behavior
   */
  private final SelectionState mySelectionState;
  @NotNull private final GlassLayer myGlassLayer;
  private final ActiveDecorationLayer myActiveDecorationLayer;

  private boolean myShowGrid = true;
  private boolean myShowComponentTags = true;
  private final DesignDropTargetListener myDropTargetListener;
  private JLabel myFormInvalidLabel;
  private final QuickFixManagerImpl myQuickFixManager;
  private final GridCaptionPanel myHorzCaptionPanel;
  private final GridCaptionPanel myVertCaptionPanel;
  private ComponentPtr mySelectionAnchor;
  private ComponentPtr mySelectionLead;
  /**
   * Undo group ID for undoing actions that need to be undone together with the form modification.
   */
  private Object myNextSaveGroupId = new Object();

  @NonNls private static final String ourHelpID = "guiDesigner.uiTour.workspace";

  public static final DataKey<GuiEditor> DATA_KEY = DataKey.create(GuiEditor.class.getName());

  /**
   * @param file file to be edited
   * @throws java.lang.IllegalArgumentException if the <code>file</code>
   *                                            is <code>null</code> or <code>file</code> is not valid PsiFile
   */
  public GuiEditor(@NotNull UIFormEditor editor, @NotNull Project project, @NotNull Module module, @NotNull VirtualFile file) {
    myEditor = editor;
    LOG.assertTrue(file.isValid());

    myProject = project;
    myModule = module;
    myFile = file;

    myCutCopyPasteSupport = new CutCopyPasteSupport(this);

    setLayout(new BorderLayout());

    myContentSplitter.setDividerWidth(0);
    myContentSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    add(myContentSplitter, BorderLayout.CENTER);

    myValidCard = new JPanel(new BorderLayout());
    myInvalidCard = createInvalidCard();

    myCardPanel.add(myValidCard, CARD_VALID);
    myCardPanel.add(myInvalidCard, CARD_INVALID);

    JPanel contentPanel = new JPanel(new LightFillLayout());
    JLabel toolbar = new JLabel();
    toolbar.setVisible(false);
    contentPanel.add(toolbar);
    contentPanel.add(myCardPanel);

    myContentSplitter.setInnerComponent(contentPanel);

    myListenerList = new EventListenerList();

    myDecorationLayer = new PassiveDecorationLayer(this);
    myDragLayer = new DragLayer(this);

    myLayeredPane = new MyLayeredPane();
    myInplaceEditingLayer = new InplaceEditingLayer(this);
    myLayeredPane.add(myInplaceEditingLayer, LAYER_INPLACE_EDITING);
    myActiveDecorationLayer = new ActiveDecorationLayer(this);
    myLayeredPane.add(myActiveDecorationLayer, LAYER_ACTIVE_DECORATION);
    myGlassLayer = new GlassLayer(this);
    myLayeredPane.add(myGlassLayer, LAYER_GLASS);
    myLayeredPane.add(myDecorationLayer, LAYER_PASSIVE_DECORATION);
    myLayeredPane.add(myDragLayer, LAYER_DND);

    myGlassLayer.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
        myDecorationLayer.repaint();
        //fireSelectedComponentChanged(); // EA-36478
      }

      public void focusLost(FocusEvent e) {
        myDecorationLayer.repaint();
      }
    });

    // Ctrl+W / Ctrl+Shift+W support
    mySelectionState = new SelectionState(this);

    // DeleteProvider
    myDeleteProvider = new MyDeleteProvider();

    // We need to synchronize GUI editor with the document
    final Alarm alarm = new Alarm();
    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(final DocumentEvent e) {
        if (!myInsideChange) {
          UndoManager undoManager = UndoManager.getInstance(getProject());
          alarm.cancelAllRequests();
          alarm.addRequest(new MySynchronizeRequest(undoManager.isUndoInProgress() || undoManager.isRedoInProgress()),
                           100/*any arbitrary delay*/, ModalityState.stateForComponent(GuiEditor.this));
        }
      }
    };

    // Prepare document
    myDocument = FileDocumentManager.getInstance().getDocument(file);
    myDocument.addDocumentListener(myDocumentListener);

    // Read form from file
    readFromFile(false);

    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(GridCaptionPanel.getGutterColor());

    myHorzCaptionPanel = new GridCaptionPanel(this, false);
    myVertCaptionPanel = new GridCaptionPanel(this, true);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.BOTH;
    panel.add(myVertCaptionPanel, gbc);

    gbc.gridx = 1;
    gbc.gridy = 0;
    panel.add(myHorzCaptionPanel, gbc);

    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;

    myScrollPane = ScrollPaneFactory.createScrollPane(myLayeredPane);
    myScrollPane.setBackground(new JBColor(() -> EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground()));
    panel.add(myScrollPane, gbc);
    myHorzCaptionPanel.attachToScrollPane(myScrollPane);
    myVertCaptionPanel.attachToScrollPane(myScrollPane);

    myValidCard.add(panel, BorderLayout.CENTER);

    final CancelCurrentOperationAction cancelCurrentOperationAction = new CancelCurrentOperationAction();
    cancelCurrentOperationAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, this);

    myProcessor = new MainProcessor(this);

    // PSI listener to restart error highlighter
    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    PsiManager.getInstance(getProject()).addPsiTreeChangeListener(myPsiTreeChangeListener);

    myQuickFixManager = new QuickFixManagerImpl(this, myGlassLayer, myScrollPane.getViewport());

    myDropTargetListener = new DesignDropTargetListener(this);
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      new DropTarget(getGlassLayer(), DnDConstants.ACTION_COPY_OR_MOVE, myDropTargetListener);
    }

    myActiveDecorationLayer.installSelectionWatcher();

    ActionManager.getInstance().getAction("GuiDesigner.IncreaseIndent").registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)), myGlassLayer);
    ActionManager.getInstance().getAction("GuiDesigner.DecreaseIndent").registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_MASK)), myGlassLayer);

    UsageTrigger.trigger("swing-designer.open");

    UIUtil.invokeLaterIfNeeded(() -> {
      DesignerToolWindowManager.getInstance(myProject).bind(this);
      PaletteToolWindowManager.getInstance(myProject).bind(this);
    });
  }

  @Override
  public ThreeComponentsSplitter getContentSplitter() {
    return myContentSplitter;
  }

  @NotNull
  public UIFormEditor getEditor() {
    return myEditor;
  }

  @NotNull
  public SelectionState getSelectionState() {
    return mySelectionState;
  }

  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myWhere != null) {
      LOG.error("Already disposed: old trace: ", myWhere);
      LOG.error("Already disposed: new trace: ");
    }
    else {
      myWhere = new Exception();
    }

    myDocument.removeDocumentListener(myDocumentListener);
    PsiManager.getInstance(getProject()).removePsiTreeChangeListener(myPsiTreeChangeListener);

    DesignerToolWindowManager.getInstance(myProject).dispose(this);
    PaletteToolWindowManager.getInstance(myProject).dispose(this);
    myPsiTreeChangeListener.dispose();

    Disposer.dispose(myContentSplitter);
  }

  @NotNull
  @Override
  public Module getModule() {
    if (myModule.isDisposed()) {
      myModule = ModuleUtil.findModuleForFile(myFile, myProject);
      if (myModule == null) {
        throw new IllegalArgumentException("No module for file " + myFile + " in project " + myModule);
      }
    }
    return myModule;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  public PsiFile getPsiFile() {
    return PsiManager.getInstance(getProject()).findFile(myFile);
  }

  public boolean isEditable() {
    final Document document = FileDocumentManager.getInstance().getDocument(myFile);
    return document != null && document.isWritable();
  }

  public boolean ensureEditable() {
    if (isEditable()) {
      return true;
    }
    VirtualFile sourceFileToCheckOut = null;
    if (!GuiDesignerConfiguration.getInstance(getProject()).INSTRUMENT_CLASSES) {
      final String classToBind = myRootContainer.getClassToBind();
      if (classToBind != null && classToBind.length() > 0) {
        PsiClass psiClass = FormEditingUtil.findClassToBind(getModule(), classToBind);
        if (psiClass != null) {
          sourceFileToCheckOut = psiClass.getContainingFile().getVirtualFile();
        }
      }
    }

    final ReadonlyStatusHandler.OperationStatus status;
    if (sourceFileToCheckOut != null) {
      status = ReadonlyStatusHandler.getInstance(getProject()).ensureFilesWritable(myFile, sourceFileToCheckOut);
    }
    else {
      status = ReadonlyStatusHandler.getInstance(getProject()).ensureFilesWritable(myFile);
    }
    return !status.hasReadonlyFiles();
  }

  public void refresh() {
    refreshImpl(myRootContainer);
    myRootContainer.getDelegee().revalidate();
    repaintLayeredPane();
  }

  public void refreshAndSave(final boolean forceSync) {
    DesignerToolWindow toolWindow = DesignerToolWindowManager.getInstance(this);
    if (toolWindow == null) {
      return;
    }

    // Update property inspector
    final PropertyInspector propertyInspector = toolWindow.getPropertyInspector();
    if (propertyInspector != null) {
      propertyInspector.synchWithTree(forceSync);
    }

    UsageTrigger.trigger("swing-designer.edit");
    refresh();
    saveToFile();
    // TODO[yole]: install appropriate listeners so that the captions repaint themselves at correct time
    myHorzCaptionPanel.repaint();
    myVertCaptionPanel.repaint();
  }

  public Object getNextSaveGroupId() {
    return myNextSaveGroupId;
  }

  private static void refreshImpl(final RadComponent component) {
    if (component.getParent() != null) {
      final Dimension size = component.getSize();
      final int oldWidth = size.width;
      final int oldHeight = size.height;
      Util.adjustSize(component.getDelegee(), component.getConstraints(), size);

      if (oldWidth != size.width || oldHeight != size.height) {
        if (component.getParent().isXY()) {
          component.setSize(size);
        }
        component.getDelegee().invalidate();
      }
    }

    if (component instanceof RadContainer) {
      component.refresh();

      final RadContainer container = (RadContainer)component;
      for (int i = container.getComponentCount() - 1; i >= 0; i--) {
        refreshImpl(container.getComponent(i));
      }
    }
  }

  public Object getData(final String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return ourHelpID;
    }

    // Standard Swing cut/copy/paste actions should work if user is editing something inside property inspector
    Project project = getProject();
    if (project.isDisposed()) return null;
    DesignerToolWindow toolWindow = DesignerToolWindowManager.getInstance(this);
    if (toolWindow == null) return null;
    final PropertyInspector inspector = toolWindow.getPropertyInspector();
    if (inspector != null && inspector.isEditing()) {
      return null;
    }

    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return myDeleteProvider;
    }

    if (PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return myCutCopyPasteSupport;
    }

    return null;
  }

  private JPanel createInvalidCard() {
    final JPanel panel = new JPanel(new GridBagLayout());
    myFormInvalidLabel = new JLabel(UIDesignerBundle.message("error.form.file.is.invalid"));
    panel.add(myFormInvalidLabel,
              new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    return panel;
  }

  /**
   * @return the component which represents DnD layer. All currently
   * dragged (moved) component are on this layer.
   */
  public DragLayer getDragLayer() {
    return myDragLayer;
  }

  /**
   * @return the topmost <code>UiConainer</code> which in the root of
   * component hierarchy. This method never returns <code>null</code>.
   */
  @NotNull
  public RadRootContainer getRootContainer() {
    return myRootContainer;
  }

  /**
   * Fires event that selection changes
   */
  public void fireSelectedComponentChanged() {
    final ComponentSelectionListener[] listeners = myListenerList.getListeners(ComponentSelectionListener.class);
    for (ComponentSelectionListener listener : listeners) {
      listener.selectedComponentChanged(this);
    }
  }

  private void fireHierarchyChanged() {
    final HierarchyChangeListener[] listeners = myListenerList.getListeners(HierarchyChangeListener.class);
    for (final HierarchyChangeListener listener : listeners) {
      listener.hierarchyChanged();
    }
  }

  @NotNull
  public GlassLayer getGlassLayer() {
    return myGlassLayer;
  }

  /**
   * @return the component which represents layer with active decorators
   * such as grid edit controls, inplace editors, etc.
   */
  public InplaceEditingLayer getInplaceEditingLayer() {
    return myInplaceEditingLayer;
  }

  @NotNull
  public JLayeredPane getLayeredPane() {
    return myLayeredPane;
  }

  public void repaintLayeredPane() {
    myLayeredPane.repaint();
  }

  /**
   * Adds specified selection listener. This listener gets notification each time
   * the selection in the component the changes.
   */
  public void addComponentSelectionListener(final ComponentSelectionListener l) {
    myListenerList.add(ComponentSelectionListener.class, l);
  }

  /**
   * Removes specified selection listener
   */
  public void removeComponentSelectionListener(final ComponentSelectionListener l) {
    myListenerList.remove(ComponentSelectionListener.class, l);
  }

  /**
   * Adds specified hierarchy change listener
   */
  public void addHierarchyChangeListener(@NotNull final HierarchyChangeListener l) {
    myListenerList.add(HierarchyChangeListener.class, l);
  }

  /**
   * Removes specified hierarchy change listener
   */
  public void removeHierarchyChangeListener(@NotNull final HierarchyChangeListener l) {
    myListenerList.remove(HierarchyChangeListener.class, l);
  }

  private void saveToFile() {
    LOG.debug("GuiEditor.saveToFile(): group ID=" + myNextSaveGroupId);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      myInsideChange = true;
      try {
        final XmlWriter writer = new XmlWriter();
        getRootContainer().write(writer);
        final String newText = writer.getText();
        final String oldText = myDocument.getText();

        try {
          final ReplaceInfo replaceInfo = findFragmentToChange(oldText, newText);
          if (replaceInfo.getStartOffset() == -1) {
            // do nothing - texts are equal
          }
          else {
            myDocument.replaceString(replaceInfo.getStartOffset(), replaceInfo.getEndOffset(), replaceInfo.getReplacement());
          }
        }
        catch (Exception e) {
          LOG.error(e);
          myDocument.replaceString(0, oldText.length(), newText);
        }
      }
      finally {
        myInsideChange = false;
      }
    }), "UI Designer Save", myNextSaveGroupId);
    myNextSaveGroupId = new Object();

    fireHierarchyChanged();
  }

  public ActiveDecorationLayer getActiveDecorationLayer() {
    return myActiveDecorationLayer;
  }

  public void setStringDescriptorLocale(final Locale locale) {
    myRootContainer.setStringDescriptorLocale(locale);
    refreshProperties();
    DesignerToolWindowManager.getInstance(this).updateComponentTree();
    DaemonCodeAnalyzer.getInstance(getProject()).restart();
  }

  @Nullable
  public Locale getStringDescriptorLocale() {
    return myRootContainer.getStringDescriptorLocale();
  }

  private void refreshProperties() {
    final Ref<Boolean> anythingModified = new Ref<>();
    FormEditingUtil.iterate(myRootContainer, new FormEditingUtil.ComponentVisitor() {
      public boolean visit(final IComponent component) {
        final RadComponent radComponent = (RadComponent)component;
        boolean componentModified = false;
        for (IProperty prop : component.getModifiedProperties()) {
          if (prop instanceof IntroStringProperty) {
            IntroStringProperty strProp = (IntroStringProperty)prop;
            componentModified = strProp.refreshValue(radComponent) || componentModified;
          }
        }

        if (component instanceof RadContainer) {
          componentModified = ((RadContainer)component).updateBorder() || componentModified;
        }

        if (component.getParentContainer() instanceof RadTabbedPane) {
          componentModified = ((RadTabbedPane)component.getParentContainer()).refreshChildTitle(radComponent) || componentModified;
        }
        if (componentModified) {
          anythingModified.set(Boolean.TRUE);
        }

        return true;
      }
    });
    if (!anythingModified.isNull()) {
      refresh();
      DesignerToolWindow designerToolWindow = DesignerToolWindowManager.getInstance(this);
      ComponentTree tree = designerToolWindow.getComponentTree();
      if (tree != null) tree.repaint();
      PropertyInspector inspector = designerToolWindow.getPropertyInspector();
      if (inspector != null) inspector.synchWithTree(true);
    }
  }

  public MainProcessor getMainProcessor() {
    return myProcessor;
  }

  public void refreshIntentionHint() {
    myQuickFixManager.refreshIntentionHint();
  }

  public void setSelectionAnchor(final RadComponent component) {
    mySelectionAnchor = new ComponentPtr(this, component);
  }

  @Nullable
  public RadComponent getSelectionAnchor() {
    if (mySelectionAnchor == null) return null;
    mySelectionAnchor.validate();
    return mySelectionAnchor.getComponent();
  }

  public void setSelectionLead(final RadComponent component) {
    mySelectionLead = new ComponentPtr(this, component);
  }

  @Nullable
  public RadComponent getSelectionLead() {
    if (mySelectionLead == null) return null;
    mySelectionLead.validate();
    return mySelectionLead.getComponent();
  }

  public void scrollComponentInView(final RadComponent component) {
    Rectangle rect = SwingUtilities.convertRectangle(component.getDelegee().getParent(), component.getBounds(), myLayeredPane);
    myLayeredPane.scrollRectToVisible(rect);
  }

  public static final class ReplaceInfo {
    private final int myStartOffset;
    private final int myEndOffset;
    private final String myReplacement;

    public ReplaceInfo(final int startOffset, final int endOffset, final String replacement) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myReplacement = replacement;
    }

    public int getStartOffset() {
      return myStartOffset;
    }

    public int getEndOffset() {
      return myEndOffset;
    }

    public String getReplacement() {
      return myReplacement;
    }
  }

  public static ReplaceInfo findFragmentToChange(final String oldText, final String newText) {
    if (oldText.equals(newText)) {
      return new ReplaceInfo(-1, -1, null);
    }

    final int oldLength = oldText.length();
    final int newLength = newText.length();

    int startOffset = 0;
    while (
      startOffset < oldLength && startOffset < newLength &&
      oldText.charAt(startOffset) == newText.charAt(startOffset)
      ) {
      startOffset++;
    }

    int endOffset = oldLength;
    while (true) {
      if (endOffset <= startOffset) {
        break;
      }
      final int idxInNew = newLength - (oldLength - endOffset) - 1;
      if (idxInNew < startOffset) {
        break;
      }

      final char c1 = oldText.charAt(endOffset - 1);
      final char c2 = newText.charAt(idxInNew);
      if (c1 != c2) {
        break;
      }
      endOffset--;
    }

    return new ReplaceInfo(startOffset, endOffset, newText.substring(startOffset, newLength - (oldLength - endOffset)));
  }

  /**
   * @param rootContainer new container to be set as a root.
   */
  private void setRootContainer(@NotNull final RadRootContainer rootContainer) {
    if (myRootContainer != null) {
      myLayeredPane.remove(myRootContainer.getDelegee());
    }
    myRootContainer = rootContainer;
    setDesignTimeInsets(2);
    myLayeredPane.add(myRootContainer.getDelegee(), LAYER_COMPONENT);

    fireHierarchyChanged();
  }

  public void setDesignTimeInsets(final int insets) {
    Integer oldInsets = (Integer)myRootContainer.getDelegee().getClientProperty(GridLayoutManager.DESIGN_TIME_INSETS);
    if (oldInsets == null || oldInsets.intValue() != insets) {
      myRootContainer.getDelegee().putClientProperty(GridLayoutManager.DESIGN_TIME_INSETS, insets);
      revalidateRecursive(myRootContainer.getDelegee());
    }
  }

  private static void revalidateRecursive(final JComponent component) {
    for (Component child : component.getComponents()) {
      if (child instanceof JComponent) {
        revalidateRecursive((JComponent)child);
      }
    }
    component.revalidate();
    component.repaint();
  }

  /**
   * Creates and sets new <code>RadRootContainer</code>
   *
   * @param keepSelection if true, the GUI designer tries to preserve the selection state after reload.
   */
  public void readFromFile(final boolean keepSelection) {
    try {
      ComponentPtr[] selection = null;
      Map<String, String> tabbedPaneSelectedTabs = null;
      if (keepSelection) {
        selection = SelectionState.getSelection(this);
        tabbedPaneSelectedTabs = saveTabbedPaneSelectedTabs();
      }
      Locale oldLocale = null;
      if (myRootContainer != null) {
        oldLocale = myRootContainer.getStringDescriptorLocale();
      }

      final String text = myDocument.getText();

      final ClassLoader classLoader = LoaderFactory.getInstance(getProject()).getLoader(myFile);

      final LwRootContainer rootContainer = Utils.getRootContainer(text, new CompiledClassPropertiesProvider(classLoader));
      final RadRootContainer container = XmlReader.createRoot(this, rootContainer, classLoader, oldLocale);
      setRootContainer(container);
      if (keepSelection) {
        SelectionState.restoreSelection(this, selection);
        restoreTabbedPaneSelectedTabs(tabbedPaneSelectedTabs);
      }
      myInvalid = false;
      myCardLayout.show(myCardPanel, CARD_VALID);
      refresh();
    }
    catch (Exception exc) {
      Throwable original = exc;
      while (original instanceof InvocationTargetException) {
        original = original.getCause();
      }
      showInvalidCard(original);
    }
    catch (final LinkageError exc) {
      showInvalidCard(exc);
    }
  }

  private void showInvalidCard(final Throwable exc) {
    LOG.info(exc);
    // setting fictive container
    setRootContainer(new RadRootContainer(this, "0"));
    myFormInvalidLabel.setText(UIDesignerBundle.message("error.form.file.is.invalid.message", FormEditingUtil.getExceptionMessage(exc)));
    myInvalid = true;
    myCardLayout.show(myCardPanel, CARD_INVALID);
    repaint();
  }

  public boolean isFormInvalid() {
    return myInvalid;
  }

  private Map<String, String> saveTabbedPaneSelectedTabs() {
    final Map<String, String> result = new HashMap<>();
    FormEditingUtil.iterate(getRootContainer(), new FormEditingUtil.ComponentVisitor() {
      public boolean visit(final IComponent component) {
        if (component instanceof RadTabbedPane) {
          RadTabbedPane tabbedPane = (RadTabbedPane)component;
          RadComponent c = tabbedPane.getSelectedTab();
          if (c != null) {
            result.put(tabbedPane.getId(), c.getId());
          }
        }
        return true;
      }
    });
    return result;
  }

  private void restoreTabbedPaneSelectedTabs(final Map<String, String> tabbedPaneSelectedTabs) {
    FormEditingUtil.iterate(getRootContainer(), new FormEditingUtil.ComponentVisitor() {
      public boolean visit(final IComponent component) {
        if (component instanceof RadTabbedPane) {
          RadTabbedPane tabbedPane = (RadTabbedPane)component;
          String selectedTabId = tabbedPaneSelectedTabs.get(tabbedPane.getId());
          if (selectedTabId != null) {
            for (RadComponent c : tabbedPane.getComponents()) {
              if (c.getId().equals(selectedTabId)) {
                tabbedPane.selectTab(c);
                break;
              }
            }
          }
        }
        return true;
      }
    });
  }

  public JComponent getPreferredFocusedComponent() {
    if (myValidCard.isVisible()) {
      return myGlassLayer;
    }
    else {
      return myInvalidCard;
    }
  }

  public static void repaintLayeredPane(final RadComponent component) {
    final GuiEditor uiEditor = (GuiEditor)SwingUtilities.getAncestorOfClass(GuiEditor.class, component.getDelegee());
    if (uiEditor != null) {
      uiEditor.repaintLayeredPane();
    }
  }

  public boolean isShowGrid() {
    return myShowGrid;
  }

  public void setShowGrid(final boolean showGrid) {
    if (myShowGrid != showGrid) {
      myShowGrid = showGrid;
      repaint();
    }
  }

  public boolean isShowComponentTags() {
    return myShowComponentTags;
  }

  public void setShowComponentTags(final boolean showComponentTags) {
    if (myShowComponentTags != showComponentTags) {
      myShowComponentTags = showComponentTags;
      repaint();
    }
  }

  public DesignDropTargetListener getDropTargetListener() {
    return myDropTargetListener;
  }

  @Nullable
  public GridCaptionPanel getFocusedCaptionPanel() {
    if (myHorzCaptionPanel.isFocusOwner()) {
      return myHorzCaptionPanel;
    }
    else if (myVertCaptionPanel.isFocusOwner()) {
      return myVertCaptionPanel;
    }
    return null;
  }

  public boolean isUndoRedoInProgress() {
    UndoManager undoManager = UndoManager.getInstance(getProject());
    return undoManager.isUndoInProgress() || undoManager.isRedoInProgress();
  }

  void hideIntentionHint() {
    myQuickFixManager.hideIntentionHint();
  }

  public void showFormSource() {
    EditorFactory editorFactory = EditorFactory.getInstance();

    Editor editor = editorFactory.createViewer(myDocument, myProject);

    try {
      ((EditorEx)editor).setHighlighter(
        new LexerEditorHighlighter(new XmlFileHighlighter(), EditorColorsManager.getInstance().getGlobalScheme()));

      JComponent component = editor.getComponent();
      component.setPreferredSize(new Dimension(640, 480));

      DialogBuilder dialog = new DialogBuilder(myProject);

      dialog.title("Form - " + myFile.getPresentableName()).dimensionKey("GuiDesigner.FormSource.Dialog");
      dialog.centerPanel(component).setPreferredFocusComponent(editor.getContentComponent());
      dialog.addOkAction();

      dialog.show();
    }
    finally {
      editorFactory.releaseEditor(editor);
    }
  }

  private final class MyLayeredPane extends JBLayeredPane implements Scrollable {
    /**
     * All components allocate whole pane's area.
     */
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
      // make sure all components fit
      int width = 0;
      int height = 0;
      for (int i = 0; i < myRootContainer.getComponentCount(); i++) {
        final RadComponent component = myRootContainer.getComponent(i);
        width = Math.max(width, component.getX() + component.getWidth());
        height = Math.max(height, component.getY() + component.getHeight());
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

  /**
   * Action works only if we are not editing something in the property inspector
   */
  private final class CancelCurrentOperationAction extends AnAction {
    public void actionPerformed(final AnActionEvent e) {
      myProcessor.cancelOperation();
      myQuickFixManager.hideIntentionHint();
    }

    public void update(final AnActionEvent e) {
      PropertyInspector inspector = DesignerToolWindowManager.getInstance(GuiEditor.this).getPropertyInspector();
      e.getPresentation().setEnabled(inspector != null && !inspector.isEditing());
    }
  }

  /**
   * Allows "DEL" button to work through the standard mechanism
   */
  private final class MyDeleteProvider implements DeleteProvider {
    public void deleteElement(@NotNull final DataContext dataContext) {
      if (!GuiEditor.this.ensureEditable()) {
        return;
      }
      CommandProcessor.getInstance().executeCommand(getProject(), () -> FormEditingUtil.deleteSelection(GuiEditor.this), UIDesignerBundle.message("command.delete.selection"), null);
    }

    public boolean canDeleteElement(@NotNull final DataContext dataContext) {
      return
        !DesignerToolWindowManager.getInstance(GuiEditor.this).getPropertyInspector().isEditing() &&
        !myInplaceEditingLayer.isEditing() &&
        FormEditingUtil.canDeleteSelection(GuiEditor.this);
    }
  }

  /**
   * Listens PSI event and update error highlighting in the UI editor
   */
  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    private final Alarm myAlarm;
    private final MyRefreshPropertiesRequest myRefreshPropertiesRequest = new MyRefreshPropertiesRequest();
    private final MySynchronizeRequest mySynchronizeRequest = new MySynchronizeRequest(true);

    public MyPsiTreeChangeListener() {
      myAlarm = new Alarm();
    }

    /**
     * Cancels all pending update requests. You have to cancel all pending requests
     * to not access to closed project.
     */
    public void dispose() {
      myAlarm.cancelAllRequests();
    }

    public void childAdded(@NotNull final PsiTreeChangeEvent event) {
      handleEvent(event);
    }

    public void childMoved(@NotNull final PsiTreeChangeEvent event) {
      handleEvent(event);
    }

    public void childrenChanged(@NotNull final PsiTreeChangeEvent event) {
      handleEvent(event);
    }

    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      handleEvent(event);
    }

    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      handleEvent(event);
    }

    public void propertyChanged(@NotNull final PsiTreeChangeEvent event) {
      if (PsiTreeChangeEvent.PROP_ROOTS.equals(event.getPropertyName())) {
        myAlarm.cancelRequest(myRefreshPropertiesRequest);
        myAlarm.addRequest(myRefreshPropertiesRequest, 500, ModalityState.stateForComponent(GuiEditor.this));
      }
    }

    private void handleEvent(final PsiTreeChangeEvent event) {
      if (event.getParent() != null) {
        PsiFile containingFile = event.getParent().getContainingFile();
        if (containingFile instanceof PropertiesFile) {
          LOG.debug("Received PSI change event for properties file");
          myAlarm.cancelRequest(myRefreshPropertiesRequest);
          myAlarm.addRequest(myRefreshPropertiesRequest, 500, ModalityState.stateForComponent(GuiEditor.this));
        }
        else if (containingFile instanceof PsiPlainTextFile && containingFile.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
          // quick check if relevant
          String resourceName = FormEditingUtil.buildResourceName(containingFile);
          if (myDocument.getText().indexOf(resourceName) >= 0) {
            LOG.debug("Received PSI change event for nested form");
            // TODO[yole]: handle multiple nesting
            myAlarm.cancelRequest(mySynchronizeRequest);
            myAlarm.addRequest(mySynchronizeRequest, 500, ModalityState.stateForComponent(GuiEditor.this));
          }
        }
      }
    }
  }

  private class MySynchronizeRequest implements Runnable {
    private final boolean myKeepSelection;

    public MySynchronizeRequest(final boolean keepSelection) {
      myKeepSelection = keepSelection;
    }

    public void run() {
      if (getModule().isDisposed()) {
        return;
      }
      Project project = getProject();
      if (project.isDisposed()) {
        return;
      }
      LOG.debug("Synchronizing GUI editor " + myFile.getName() + " to document");
      PsiDocumentManager.getInstance(project).commitDocument(myDocument);
      readFromFile(myKeepSelection);
    }
  }

  private class MyRefreshPropertiesRequest implements Runnable {
    public void run() {
      if (!getModule().isDisposed() && !getProject().isDisposed() && !DumbService.isDumb(getProject())) {
        refreshProperties();
      }
    }
  }

  public void paletteKeyPressed(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_SHIFT && PaletteToolWindowManager.getInstance(this).getActiveItem(ComponentItem.class) != null) {
      setDesignTimeInsets(12);
    }
  }

  public void paletteKeyReleased(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
      setDesignTimeInsets(2);
    }
  }

  public void paletteDropActionChanged(int gestureModifiers) {
    if ((gestureModifiers & InputEvent.SHIFT_MASK) != 0) {
      setDesignTimeInsets(12);
    }
    else {
      setDesignTimeInsets(2);
    }
  }

  public void paletteValueChanged(ListSelectionEvent e) {
    if (PaletteToolWindowManager.getInstance(this).getActiveItem() == null) {
      myProcessor.cancelPaletteInsert();
    }
  }
}