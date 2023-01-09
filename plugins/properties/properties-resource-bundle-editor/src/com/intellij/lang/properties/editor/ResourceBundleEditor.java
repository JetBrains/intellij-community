// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.properties.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.inspections.incomplete.IncompletePropertyInspection;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertiesResourceBundleUtil;
import com.intellij.lang.properties.xml.XmlPropertiesFile;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceBundleEditor extends UserDataHolderBase implements DocumentsEditor {
  private static final Logger LOG = Logger.getInstance(ResourceBundleEditor.class);
  @NonNls private static final String VALUES               = "values";
  @NonNls private static final String NO_PROPERTY_SELECTED = "noPropertySelected";
  public static final Key<ResourceBundleEditor> RESOURCE_BUNDLE_EDITOR_KEY = Key.create("resourceBundleEditor");

  private final Project myProject;
  private final VirtualFile myFile;

  private final StructureViewComponent      myStructureViewComponent;
  private final Map<VirtualFile, EditorEx> myEditors;
  private final ResourceBundle              myResourceBundle;
  private final ResourceBundlePropertiesUpdateManager myPropertiesInsertDeleteManager;
  private final Map<VirtualFile, JPanel> myTitledPanels;
  private final JComponent                    myNoPropertySelectedPanel = new NoPropertySelectedPanel().getComponent();
  private final DataProviderPanel myDataProviderPanel;
  // user pressed backslash in the corresponding editor.
  // we cannot store it back to properties file right now, so just append the backslash to the editor and wait for the subsequent chars
  private final Set<VirtualFile> myBackSlashPressed     = new HashSet<>();
  private final Alarm               mySelectionChangeAlarm = new Alarm();

  private final JPanel              myValuesPanel;
  private final JPanel              myStructureViewPanel;
  private volatile boolean    myDisposed;
  private final ResourceBundleEditorFileListener myVfsListener;
  private Editor              mySelectedEditor;
  private String              myPropertyToSelectWhenVisible;
  private final ResourceBundleEditorHighlighter myHighlighter;

  public ResourceBundleEditor(@NotNull Project project, @NotNull VirtualFile file, @NotNull ResourceBundle resourceBundle) {
    myProject = project;
    myFile = file;

    final JPanel splitPanel = new JPanel();
    myValuesPanel = new JPanel();
    myStructureViewPanel = new JPanel();
    JBSplitter splitter = new OnePixelSplitter(false);
    splitter.setFirstComponent(myStructureViewPanel);
    splitter.setSecondComponent(myValuesPanel);
    splitter.setShowDividerControls(true);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setAndLoadSplitterProportionKey(getClass() + ".splitter");
    splitPanel.setLayout(new BorderLayout());
    splitPanel.add(splitter, BorderLayout.CENTER);

    myResourceBundle = resourceBundle;
    myPropertiesInsertDeleteManager = new ResourceBundlePropertiesUpdateManager(resourceBundle);

    myStructureViewComponent = new ResourceBundleStructureViewComponent(myResourceBundle, this);
    myStructureViewPanel.setLayout(new BorderLayout());
    myStructureViewPanel.add(myStructureViewComponent, BorderLayout.CENTER);

    myStructureViewComponent.getTree().getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      private IProperty selectedProperty;
      private VirtualFile selectedPropertiesFile;

      @Override
      public void valueChanged(TreeSelectionEvent e) {
        // filter out temp unselect/select events
        if (getSelectedElementIfOnlyOne() instanceof ResourceBundleFileStructureViewElement) {
          ((CardLayout)myValuesPanel.getLayout()).show(myValuesPanel, NO_PROPERTY_SELECTED);
          writePreviouslySelectedPropertyValue(e);
          selectedPropertiesFile = null;
          selectedProperty = null;
          return;
        }

        if (Comparing.equal(e.getNewLeadSelectionPath(), e.getOldLeadSelectionPath()) || getSelectedProperty() == null) return;
        if (!arePropertiesEquivalent(selectedProperty, getSelectedProperty()) ||
            !Comparing.equal(selectedPropertiesFile, getSelectedPropertiesFile())) {
          writePreviouslySelectedPropertyValue(e);
          selectedProperty = getSelectedProperty();
          selectedPropertiesFile = getSelectedPropertiesFile();
          selectionChanged();
        }
      }

      private void writePreviouslySelectedPropertyValue(TreeSelectionEvent e) {
        if (selectedProperty != null && e.getOldLeadSelectionPath() != null) {
          for (Map.Entry<VirtualFile, EditorEx> entry : myEditors.entrySet()) {
            if (entry.getValue() == mySelectedEditor) {
              writeEditorPropertyValue(selectedProperty.getName(), mySelectedEditor, entry.getKey());
              break;
            }
          }
        }
      }

      private boolean arePropertiesEquivalent(@Nullable IProperty oldSelected, @Nullable IProperty newSelected) {
        if (oldSelected == newSelected) {
          return true;
        }
        if (oldSelected == null || newSelected == null) {
          return false;
        }
        final PsiElement oldPsiElement = oldSelected.getPsiElement();
        if (!oldPsiElement.isValid()) {
          return false;
        }
        return oldPsiElement.isEquivalentTo(newSelected.getPsiElement());
      }
    });

    myEditors = new ConcurrentHashMap<>();
    myTitledPanels = new HashMap<>();
    recreateEditorsPanel();

    TreeElement[] children = myStructureViewComponent.getTreeModel().getRoot().getChildren();
    if (children.length != 0) {
      TreeElement child = children[0];
      IProperty property = ((PropertyStructureViewElement)child).getProperty();
      if (property != null) {
        String propName = property.getUnescapedKey();
        setState(new ResourceBundleEditorState(propName));
      }
    }
    myDataProviderPanel = new DataProviderPanel(splitPanel);

    myVfsListener = installPropertiesChangeListeners();

    myProject.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        onSelectionChanged(event);
      }
    });
    myHighlighter = myResourceBundle.getDefaultPropertiesFile() instanceof XmlPropertiesFile ? null : new ResourceBundleEditorHighlighter(this);
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  public ResourceBundle getResourceBundle() {
    return myResourceBundle;
  }

  public void updateTreeRoot() {
    myStructureViewComponent.rebuild();
  }

  @NotNull
  public ResourceBundlePropertiesUpdateManager getPropertiesInsertDeleteManager() {
    return myPropertiesInsertDeleteManager;
  }

  private void onSelectionChanged(@NotNull FileEditorManagerEvent event) {
    if (!myResourceBundle.isValid()) return;
    // Ignore events which don't target current editor.
    FileEditor oldEditor = event.getOldEditor();
    FileEditor newEditor = event.getNewEditor();
    if (oldEditor != this && newEditor != this) {
      return;
    }

    // We want to sync selected property key on selection change.
    if (newEditor == this) {
      if (oldEditor instanceof TextEditor) {
        myPropertiesInsertDeleteManager.reload();
        setStructureViewSelectionFromPropertiesFile(((TextEditor)oldEditor).getEditor());
      } else if (myPropertyToSelectWhenVisible != null) {
        setStructureViewSelection(myPropertyToSelectWhenVisible);
        myPropertyToSelectWhenVisible = null;
      }
    }
    else if (newEditor instanceof TextEditor) {
      setPropertiesFileSelectionFromStructureView(((TextEditor)newEditor).getEditor());
    }
  }

  private void setStructureViewSelectionFromPropertiesFile(@NotNull Editor propertiesFileEditor) {
    int line = propertiesFileEditor.getCaretModel().getLogicalPosition().line;
    Document document = propertiesFileEditor.getDocument();
    if (line >= document.getLineCount()) {
      return;
    }
    final String propertyName = getPropertyName(document, line);
    if (propertyName == null) {
      return;
    }
    setStructureViewSelection(propertyName);
  }

  private void setStructureViewSelection(@NotNull final String propertyName) {
    if (myStructureViewComponent.isDisposed()) {
      return;
    }
    JTree tree = myStructureViewComponent.getTree();
    Object root = tree.getModel().getRoot();
    if (AbstractTreeUi.isLoadingChildrenFor(root)) {
      boolean isEditorVisible = false;
      for (FileEditor editor : FileEditorManager.getInstance(myProject).getSelectedEditors()) {
        if (editor == this) {
          isEditorVisible = true;
          break;
        }
      }
      if (!isEditorVisible) {
        myPropertyToSelectWhenVisible = propertyName;
        return;
      }
      mySelectionChangeAlarm.cancelAllRequests();
      mySelectionChangeAlarm.addRequest(() -> {
        mySelectionChangeAlarm.cancelAllRequests();
        setStructureViewSelection(propertyName);
      }, 500);
      return;
    }

    Stack<TreeElement> toCheck = new Stack<>();
    toCheck.push(myStructureViewComponent.getTreeModel().getRoot());

    while (!toCheck.isEmpty()) {
      TreeElement element = toCheck.pop();
      PsiElement value = element instanceof PropertyStructureViewElement
                     ? ((PropertyStructureViewElement)element).getPsiElement()
                     : null;
      final IProperty property = PropertiesImplUtil.getProperty(value);
      if (property != null && propertyName.equals(property.getUnescapedKey())) {
        myStructureViewComponent.select(property, true);
        selectionChanged();
        return;
      } else {
        for (TreeElement treeElement : element.getChildren()) {
          toCheck.push(treeElement);
        }
      }
    }
  }

  public void flush() {
    myVfsListener.flush();
  }

  @Nullable
  private static String getPropertyName(@NotNull Document document, int line) {
    int startOffset = document.getLineStartOffset(line);
    int endOffset = StringUtil.indexOf(document.getCharsSequence(), '=', startOffset, document.getLineEndOffset(line));
    if (endOffset <= startOffset) {
      return null;
    }
    String propertyName = document.getCharsSequence().subSequence(startOffset, endOffset).toString().trim();
    return propertyName.isEmpty() ? null : propertyName;
  }

  private void setPropertiesFileSelectionFromStructureView(@NotNull Editor propertiesFileEditor) {
    String selectedPropertyName = getSelectedPropertyName();
    if (selectedPropertyName == null) {
      return;
    }

    Document document = propertiesFileEditor.getDocument();
    for (int i = 0; i < document.getLineCount(); i++) {
      String propertyName = getPropertyName(document, i);
      if (selectedPropertyName.equals(propertyName)) {
        propertiesFileEditor.getCaretModel().moveToLogicalPosition(new LogicalPosition(i, 0));
        return;
      }
    }
  }

  private void writeEditorPropertyValue(final @Nullable String propertyName,
                                        final @NotNull Editor editor,
                                        final @NotNull VirtualFile file) {
    final String currentValue = editor.getDocument().getText();
    final String currentSelectedProperty = propertyName ==  null ? getSelectedPropertyName() : propertyName;
    if (currentSelectedProperty == null) {
      return;
    }
    if (!FileModificationService.getInstance().prepareVirtualFilesForWrite(myProject, Collections.singleton(file))) {
      return;
    }
    WriteAction.run(() -> WriteCommandAction.runWriteCommandAction(myProject, ResourceBundleEditorBundle.message(
      "resource.bundle.update.property.value"), null, () -> {
      PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file, myProject);
      if (propertiesFile != null) {
        if (currentValue.isEmpty() &&
            ResourceBundleEditorKeepEmptyValueToggleAction.keepEmptyProperties() &&
            !file.equals(myResourceBundle.getDefaultPropertiesFile().getVirtualFile())) {
          myPropertiesInsertDeleteManager.deletePropertyIfExist(currentSelectedProperty, propertiesFile);
        } else {
          myPropertiesInsertDeleteManager.insertOrUpdateTranslation(currentSelectedProperty, currentValue, propertiesFile);
        }
      }
    }));
  }

  void recreateEditorsPanel() {
    if (!myProject.isOpen() || myDisposed) return;

    myValuesPanel.removeAll();
    myValuesPanel.setLayout(new CardLayout());

    JPanel valuesPanelComponent = new MyJPanel(new GridBagLayout());
    myValuesPanel.add(new JBScrollPane(valuesPanelComponent){
      @Override
      public void updateUI() {
        super.updateUI();
        getViewport().setBackground(UIUtil.getPanelBackground());
      }
    }, VALUES);
    myValuesPanel.add(myNoPropertySelectedPanel, NO_PROPERTY_SELECTED);

    final List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles();

    GridBagConstraints gc = new GridBagConstraints(0, 0, 0, 0, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                   JBUI.insets(5), 0, 0);
    releaseAllEditors();
    myTitledPanels.clear();
    int y = 0;
    Editor previousEditor = null;
    Editor firstEditor = null;
    for (final PropertiesFile propertiesFile : propertiesFiles) {
      final EditorEx editor = createEditor();
      final Editor oldEditor = myEditors.put(propertiesFile.getVirtualFile(), editor);
      if (firstEditor == null) {
        firstEditor = editor;
      }
      if (previousEditor != null) {
        editor.putUserData(ChooseSubsequentPropertyValueEditorAction.PREV_EDITOR_KEY, previousEditor);
        previousEditor.putUserData(ChooseSubsequentPropertyValueEditorAction.NEXT_EDITOR_KEY, editor);
      }
      previousEditor = editor;
      if (oldEditor != null) {
        EditorFactory.getInstance().releaseEditor(oldEditor);
      }

      editor.setViewer(!propertiesFile.getVirtualFile().isWritable());
      editor.getContentComponent().addKeyListener(new KeyAdapter() {
        @Override
        public void keyTyped(KeyEvent e) {
          if (editor.isViewer()) {
            editor.setViewer(ReadonlyStatusHandler.getInstance(myProject)
                               .ensureFilesWritable(Collections.singletonList(propertiesFile.getVirtualFile())).hasReadonlyFiles());
          }
        }
      });

      editor.addFocusListener(new FocusChangeListener() {
        @Override
        public void focusGained(@NotNull final Editor editor) {
          mySelectedEditor = editor;
        }

        @Override
        public void focusLost(@NotNull final Editor editor) {
          if (!editor.isViewer() && propertiesFile.getContainingFile().isValid()) {
            writeEditorPropertyValue(null, editor, propertiesFile.getVirtualFile());
            myVfsListener.flush();
          }
        }
      });
      gc.gridx = 0;
      gc.gridy = y++;
      gc.gridheight = 1;
      gc.gridwidth = GridBagConstraints.REMAINDER;
      gc.weightx = 1;
      gc.weighty = 1;
      gc.anchor = GridBagConstraints.CENTER;

      String title = propertiesFile.getName() + PropertiesUtil.getPresentableLocale(propertiesFile.getLocale());
      JPanel comp = new JPanel(new BorderLayout()) {
        @Override
        public Dimension getPreferredSize() {
          Insets insets = getBorder().getBorderInsets(this);
          return new Dimension(100,editor.getLineHeight()*4+ insets.top + insets.bottom);
        }
      };
      comp.add(editor.getComponent(), BorderLayout.CENTER);
      comp.setBorder(IdeBorderFactory.createTitledBorder(title, false));
      myTitledPanels.put(propertiesFile.getVirtualFile(), comp);

      valuesPanelComponent.add(comp, gc);
    }
    if (previousEditor != null) {
      previousEditor.putUserData(ChooseSubsequentPropertyValueEditorAction.NEXT_EDITOR_KEY, firstEditor);
      firstEditor.putUserData(ChooseSubsequentPropertyValueEditorAction.PREV_EDITOR_KEY, previousEditor);
    }

    gc.gridx = 0;
    gc.gridy = y;
    gc.gridheight = GridBagConstraints.REMAINDER;
    gc.gridwidth = GridBagConstraints.REMAINDER;
    gc.weightx = 10;
    gc.weighty = 1;

    valuesPanelComponent.add(new JPanel(), gc);
    selectionChanged();
    myValuesPanel.repaint();
    updateEditorsFromProperties(true);
  }

  @NotNull
  public static String getPropertyEditorValue(@Nullable final IProperty property) {
    if (property == null) {
      return "";
    }
    else {
      String rawValue = property.getValue();
      return rawValue == null ? "" : PropertiesResourceBundleUtil.fromPropertyValueToValueEditor(rawValue);
    }
  }

  void updateEditorsFromProperties(final boolean checkIsUnderUndoRedoAction) {
    String propertyName = getSelectedPropertyName();
    ((CardLayout)myValuesPanel.getLayout()).show(myValuesPanel, propertyName == null ? NO_PROPERTY_SELECTED : VALUES);
    if (propertyName == null) return;

    final UndoManagerImpl undoManager = (UndoManagerImpl)UndoManager.getInstance(myProject);
    for (final PropertiesFile propertiesFile : myResourceBundle.getPropertiesFiles()) {
      final EditorEx editor = myEditors.get(propertiesFile.getVirtualFile());
      if (editor == null) continue;
      final IProperty property = propertiesFile.findPropertyByKey(propertyName);
      final Document document = editor.getDocument();
      CommandProcessor.getInstance().executeCommand(null, () -> ApplicationManager.getApplication().runWriteAction(() -> {
        if (!checkIsUnderUndoRedoAction || !undoManager.isActive() || !undoManager.isUndoOrRedoInProgress()) {
          updateDocumentFromPropertyValue(getPropertyEditorValue(property), document,  propertiesFile.getVirtualFile());
        }
      }), "", this);
      JPanel titledPanel = myTitledPanels.get(propertiesFile.getVirtualFile());
      ((TitledBorder)titledPanel.getBorder()).setTitleColor(property == null ? JBColor.RED : UIUtil.getLabelTextForeground());
      titledPanel.repaint();
    }
  }

  @NotNull
  private ResourceBundleEditorFileListener installPropertiesChangeListeners() {
    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    ResourceBundleEditorFileListener myVfsListener = new ResourceBundleEditorFileListener(this);

    virtualFileManager.addVirtualFileListener(myVfsListener, this);
    PsiTreeChangeAdapter psiTreeChangeAdapter = new PsiTreeChangeAdapter() {
      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        final PsiFile file = event.getFile();
        if (file instanceof XmlFile) {
          final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
          if (propertiesFile != null) {
            final ResourceBundle bundle = propertiesFile.getResourceBundle();
            if (bundle.equals(myResourceBundle) && !myEditors.containsKey(propertiesFile.getVirtualFile())) {
              recreateEditorsPanel();
            }
          }
        }
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        final PsiFile file = event.getFile();
        final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
        if (propertiesFile != null) {
          final ResourceBundle bundle = propertiesFile.getResourceBundle();
          if (bundle.equals(myResourceBundle) && myEditors.containsKey(propertiesFile.getVirtualFile())) {
            final IProperty property = PropertiesImplUtil.getProperty(event.getParent());
            if (property != null && Objects.equals(property.getName(), getSelectedPropertyName())) {
              updateEditorsFromProperties(false);
            }
          }
        }
      }
    };
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(psiTreeChangeAdapter, this);
    return myVfsListener;
  }

  private void selectionChanged() {
    myBackSlashPressed.clear();
    UIUtil.invokeLaterIfNeeded(() -> {
      updateEditorsFromProperties(true);
      final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
      if (statusBar != null) {
        statusBar.setInfo(ResourceBundleEditorBundle.message("status.bar.selection.changed.message", getSelectedPropertyName()));
      }
    });
  }

  private void updateDocumentFromPropertyValue(final String value,
                                               final Document document,
                                               final VirtualFile propertiesFile) {
    @NonNls String text = myBackSlashPressed.contains(propertiesFile) ? value + "\\" : value;
    UndoUtil.disableUndoIn(document, () -> document.replaceString(0, document.getTextLength(), text));
  }

  @NotNull
  private JBIterable<Object> getSelectedNodes() {
    if (!isValid()) {
      return JBIterable.empty();
    }
    JTree tree = myStructureViewComponent.getTree();
    return JBIterable.of(tree.getSelectionModel().getSelectionPaths())
      .map(TreeUtil::getLastUserObject);
  }

  @Nullable
  private String getSelectedPropertyName() {
    final IProperty selectedProperty = getSelectedProperty();
    return selectedProperty == null ? null : selectedProperty.getName();
  }

  @Nullable
  IProperty getSelectedProperty() {
    ResourceBundleEditorViewElement first = getSelectedNodes()
      .filter(AbstractTreeNode.class)
      .filterMap(AbstractTreeNode::getValue)
      .filter(ResourceBundleEditorViewElement.class)
      .first();
    return first instanceof PropertyStructureViewElement ?
           ((PropertyStructureViewElement)first).getProperty() : null;
  }

  @NotNull
  public Collection<ResourceBundleEditorViewElement> getSelectedElements() {
    return getSelectedNodes()
      .filter(AbstractTreeNode.class)
      .filterMap(AbstractTreeNode::getValue)
      .filter(ResourceBundleEditorViewElement.class)
      .toList();
  }

  @NotNull
  public Collection<Object> getSelectedObjects() {
    return getSelectedNodes()
      .filter(AbstractTreeNode.class)
      .filterMap(AbstractTreeNode::getValue)
      .toList();
  }

  @Nullable
  public Object getSelectedElementIfOnlyOne() {
    final Collection<Object> selectedElements = getSelectedObjects();
    return selectedElements.size() == 1 ? ContainerUtil.getFirstItem(selectedElements) : null;
  }

  public void selectNextIncompleteProperty() {
    if (getSelectedNodes().size() != 1) {
      return;
    }
    final IProperty selectedProperty = getSelectedProperty();
    if (selectedProperty == null) {
      return;
    }

    final ResourceBundleFileStructureViewElement root =
      (ResourceBundleFileStructureViewElement)myStructureViewComponent.getTreeModel().getRoot();
    final Set<String> propertyKeys = ResourceBundleFileStructureViewElement.getPropertiesMap(myResourceBundle, root.isShowOnlyIncomplete()).keySet();
    final boolean isAlphaSorted = myStructureViewComponent.isActionActive(Sorter.ALPHA_SORTER_ID);
    List<String> keysOrder = isAlphaSorted ? ContainerUtil.sorted(propertyKeys) : new ArrayList<>(propertyKeys);

    final String currentKey = selectedProperty.getKey();
    final int idx = keysOrder.indexOf(currentKey);
    LOG.assertTrue(idx != -1);
    final IncompletePropertyInspection incompletePropertyInspection =
      IncompletePropertyInspection.getInstance(myResourceBundle.getDefaultPropertiesFile().getContainingFile());
    for (int i = 1; i < keysOrder.size(); i++) {
      int trimmedIndex = (i + idx) % keysOrder.size();
      final String key = keysOrder.get(trimmedIndex);
      if (!incompletePropertyInspection.isPropertyComplete(key, myResourceBundle)) {
        selectProperty(key);
        return;
      }
    }
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myDataProviderPanel;
  }

  public StructureViewComponent getStructureViewComponent() {
    return myStructureViewComponent;
  }

  private Object getData(@NotNull String dataId) {
    if (SelectInContext.DATA_KEY.is(dataId)) {
      VirtualFile file = getSelectedPropertiesFile();
      return file == null ? null : new FileSelectInContext(myProject, file);
    }
    return null;
  }

  private VirtualFile getSelectedPropertiesFile() {
    if (mySelectedEditor == null) return null;
    VirtualFile selectedFile = null;
    for (Map.Entry<VirtualFile, EditorEx> entry : myEditors.entrySet()) {
      Editor editor = entry.getValue();
      if (editor == mySelectedEditor) {
        selectedFile = entry.getKey();
        break;
      }
    }
    return selectedFile;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStructureViewPanel;
  }

  @Override
  @NotNull
  public String getName() {
    return ResourceBundleEditorBundle.message("resource.bundle.editor.title");
  }

  @Override
  @NotNull
  public ResourceBundleEditorState getState(@NotNull FileEditorStateLevel level) {
    return new ResourceBundleEditorState(getSelectedPropertyName());
  }

  public void selectProperty(@Nullable final String propertyName) {
    if (propertyName != null) {
      setStructureViewSelection(propertyName);
    }
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    selectProperty(((ResourceBundleEditorState)state).getPropertyName());
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return !myDisposed && !myProject.isDisposed();
  }

  @Override
  public void deselectNotify() {
    final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (statusBar != null) {
      statusBar.setInfo("");
    }
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return myHighlighter;
  }

  @Override
  public void dispose() {
    if (mySelectedEditor != null) {
      for (final Map.Entry<VirtualFile, EditorEx> entry : myEditors.entrySet()) {
        if (mySelectedEditor.equals(entry.getValue())) {
          writeEditorPropertyValue(null, mySelectedEditor, entry.getKey());
        }
      }
    }
    myDisposed = true;
    Disposer.dispose(myStructureViewComponent);
    releaseAllEditors();
  }

  private void releaseAllEditors() {
    for (final Editor editor : myEditors.values()) {
      if (!editor.isDisposed()) {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    }
    myEditors.clear();
  }

  @Override
  public @NotNull Document @NotNull [] getDocuments() {
    return ContainerUtil.mapNotNull(myEditors.keySet(), propertiesFile -> FileDocumentManager.getInstance().getDocument(propertiesFile))
      .toArray(Document.EMPTY_ARRAY);
  }

  Map<VirtualFile, EditorEx> getTranslationEditors() {
    return myEditors;
  }

  public static class ResourceBundleEditorState implements FileEditorState {
    private final String myPropertyName;

    public ResourceBundleEditorState(String propertyName) {
      myPropertyName = propertyName;
    }

    @Override
    public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
      return false;
    }

    public String getPropertyName() {
      return myPropertyName;
    }
  }

  private EditorEx createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument("");
    EditorEx editor = (EditorEx)editorFactory.createEditor(document);
    reinitSettings(editor);
    editor.putUserData(RESOURCE_BUNDLE_EDITOR_KEY, this);
    return editor;
  }

  private void reinitSettings(final EditorEx editor) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    editor.setColorsScheme(scheme);
    editor.setBorder(BorderFactory.createLineBorder(JBColor.border(), 1));
    EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(false);
    settings.setWhitespacesShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setRightMarginShown(true);
    settings.setRightMargin(60);
    settings.setVirtualSpace(false);
    editor.setHighlighter(new LexerEditorHighlighter(new PropertiesValueHighlighter(), scheme));
    editor.setVerticalScrollbarVisible(true);
    editor.installPopupHandler(new ContextMenuPopupHandler() {
      @Override
      public ActionGroup getActionGroup(@NotNull EditorMouseEvent event) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_CUT_COPY_PASTE));
        group.add(CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.ACTION_EDIT_SOURCE));
        group.addSeparator();
        group.add(new AnAction(ResourceBundleEditorBundle.messagePointer("action.PropagateValue.text")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            final String valueToPropagate = editor.getDocument().getText();
            final String currentSelectedProperty = getSelectedPropertyName();
            if (currentSelectedProperty == null) {
              return;
            }
            ApplicationManager.getApplication().runWriteAction(() -> WriteCommandAction.runWriteCommandAction(myProject, ResourceBundleEditorBundle.message("action.PropagateValue.text"), null, () -> {
              try {
                final PropertiesFile[] propertiesFiles = myResourceBundle.getPropertiesFiles().stream().filter(f -> {
                  final IProperty property = f.findPropertyByKey(currentSelectedProperty);
                  return property == null || !valueToPropagate.equals(property.getValue());
                }).toArray(PropertiesFile[]::new);
                final PsiFile[] filesToPrepare = Arrays.stream(propertiesFiles).map(PropertiesFile::getContainingFile).toArray(PsiFile[]::new);
                if (FileModificationService.getInstance().preparePsiElementsForWrite(filesToPrepare)) {
                  for (PropertiesFile file : propertiesFiles) {
                    myPropertiesInsertDeleteManager.insertOrUpdateTranslation(currentSelectedProperty, valueToPropagate, file);
                  }
                  recreateEditorsPanel();
                }
              }
              catch (final IncorrectOperationException e1) {
                LOG.error(e1);
              }
            }));
          }
        });
        return group;
      }
    });
  }

  private final class DataProviderPanel extends JPanel implements DataProvider {
    private DataProviderPanel(final JPanel panel) {
      super(new BorderLayout());
      add(panel, BorderLayout.CENTER);
    }

    @Override
    @Nullable
    public Object getData(@NotNull String dataId) {
      return ResourceBundleEditor.this.getData(dataId);
    }
  }

  private final class MyJPanel extends JPanel implements Scrollable{
    private MyJPanel(LayoutManager layout) {
      super(layout);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      Editor editor = myEditors.values().iterator().next();
      return editor.getLineHeight()*4;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      return visibleRect.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }
}
