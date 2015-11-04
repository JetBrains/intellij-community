/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * @author Alexey
 */
package com.intellij.lang.properties.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ide.util.treeView.smartTree.CachingChildrenTreeNode;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.inspections.incomplete.IncompletePropertyInspection;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertiesResourceBundleUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class ResourceBundleEditor extends UserDataHolderBase implements DocumentsEditor {
  private static final         Logger LOG                  =
    Logger.getInstance("#com.intellij.lang.properties.editor.ResourceBundleEditor");
  @NonNls private static final String VALUES               = "values";
  @NonNls private static final String NO_PROPERTY_SELECTED = "noPropertySelected";
  public static final Key<ResourceBundleEditor> RESOURCE_BUNDLE_EDITOR_KEY = Key.create("resourceBundleEditor");

  private final StructureViewComponent      myStructureViewComponent;
  private final Map<PropertiesFile, Editor> myEditors;
  private final ResourceBundle              myResourceBundle;
  private final ResourceBundlePropertiesUpdateManager myPropertiesInsertDeleteManager;
  private final Map<PropertiesFile, JPanel> myTitledPanels;
  private final JComponent                    myNoPropertySelectedPanel = new NoPropertySelectedPanel().getComponent();
  private final Project           myProject;
  private final DataProviderPanel myDataProviderPanel;
  // user pressed backslash in the corresponding editor.
  // we cannot store it back to properties file right now, so just append the backslash to the editor and wait for the subsequent chars
  private final Set<PropertiesFile> myBackSlashPressed     = new THashSet<PropertiesFile>();
  private final Alarm               mySelectionChangeAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final PropertiesAnchorizer myPropertiesAnchorizer;

  private JPanel              myValuesPanel;
  private JPanel              myStructureViewPanel;
  private boolean             myDisposed;
  private VirtualFileListener myVfsListener;
  private Editor              mySelectedEditor;
  private String              myPropertyToSelectWhenVisible;

  public ResourceBundleEditor(@NotNull ResourceBundle resourceBundle) {
    myProject = resourceBundle.getProject();

    final JPanel splitPanel = new JPanel();
    myValuesPanel = new JPanel();
    myStructureViewPanel = new JPanel();
    JBSplitter splitter = new JBSplitter(false);
    splitter.setFirstComponent(myStructureViewPanel);
    splitter.setSecondComponent(myValuesPanel);
    splitter.setShowDividerControls(true);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setAndLoadSplitterProportionKey(getClass() + ".splitter");
    splitPanel.setLayout(new BorderLayout());
    splitPanel.add(splitter, BorderLayout.CENTER);

    myResourceBundle = resourceBundle;
    myPropertiesInsertDeleteManager = new ResourceBundlePropertiesUpdateManager(resourceBundle);

    myPropertiesAnchorizer = new PropertiesAnchorizer(myResourceBundle.getProject());
    myStructureViewComponent = new ResourceBundleStructureViewComponent(myResourceBundle, this, myPropertiesAnchorizer);
    myStructureViewPanel.setLayout(new BorderLayout());
    myStructureViewPanel.add(myStructureViewComponent, BorderLayout.CENTER);

    myStructureViewComponent.getTree().getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      private IProperty selectedProperty;
      private PropertiesFile selectedPropertiesFile;

      @Override
      public void valueChanged(TreeSelectionEvent e) {
        // filter out temp unselect/select events
        if (getSelectedElementIfOnlyOne() instanceof ResourceBundleFileStructureViewElement) {
          ((CardLayout)myValuesPanel.getLayout()).show(myValuesPanel, NO_PROPERTY_SELECTED);
          selectedPropertiesFile = null;
          selectedProperty = null;
          return;
        }

        if (Comparing.equal(e.getNewLeadSelectionPath(), e.getOldLeadSelectionPath()) || getSelectedProperty() == null) return;
        if (!arePropertiesEquivalent(selectedProperty, getSelectedProperty()) ||
            !Comparing.equal(selectedPropertiesFile, getSelectedPropertiesFile())) {

          if (selectedProperty != null && e.getOldLeadSelectionPath() != null) {
            for (Map.Entry<PropertiesFile, Editor> entry : myEditors.entrySet()) {
              if (entry.getValue() == mySelectedEditor) {
                writeEditorPropertyValue(selectedProperty.getName(), mySelectedEditor, entry.getKey());
                break;
              }
            }
          }

          selectedProperty = getSelectedProperty();
          selectedPropertiesFile = getSelectedPropertiesFile();
          selectionChanged();
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
    installPropertiesChangeListeners();

    myEditors = new THashMap<PropertiesFile, Editor>();
    myTitledPanels = new THashMap<PropertiesFile, JPanel>();
    recreateEditorsPanel();

    TreeElement[] children = myStructureViewComponent.getTreeModel().getRoot().getChildren();
    if (children.length != 0) {
      TreeElement child = children[0];
      String propName = ((ResourceBundlePropertyStructureViewElement)child).getProperty().getUnescapedKey();
      setState(new ResourceBundleEditorState(propName));
    }
    myDataProviderPanel = new DataProviderPanel(splitPanel);

    myProject.getMessageBus().connect(myProject).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        onSelectionChanged(event);
      }
    });
  }

  public ResourceBundle getResourceBundle() {
    return myResourceBundle;
  }

  public void updateTreeRoot() {
    final Object element = myStructureViewComponent.getTreeStructure().getRootElement();
    if (element instanceof CachingChildrenTreeNode) {
      ((CachingChildrenTreeNode)element).rebuildChildren();
    }
  }

  @NotNull
  public ResourceBundlePropertiesUpdateManager getPropertiesInsertDeleteManager() {
    return myPropertiesInsertDeleteManager;
  }

  private void onSelectionChanged(@NotNull FileEditorManagerEvent event) {
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
    if (tree == null) {
      return;
    }

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
      mySelectionChangeAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          mySelectionChangeAlarm.cancelAllRequests();
          setStructureViewSelection(propertyName);
        }
      }, 500);
      return;
    }

    Stack<TreeElement> toCheck = ContainerUtilRt.newStack();
    toCheck.push(myStructureViewComponent.getTreeModel().getRoot());

    while (!toCheck.isEmpty()) {
      TreeElement element = toCheck.pop();
      PsiElement value = element instanceof ResourceBundlePropertyStructureViewElement
                     ? ((ResourceBundlePropertyStructureViewElement)element).getProperty().getPsiElement()
                     : null;
      if (value != null) {
        final IProperty property = PropertiesImplUtil.getProperty(value);
        if (propertyName.equals(property.getUnescapedKey())) {
          final PropertiesAnchorizer.PropertyAnchor anchor = myPropertiesAnchorizer.get(property);
          myStructureViewComponent.select(anchor, true);
          selectionChanged();
          return;
        }
      }
      else {
        for (TreeElement treeElement : element.getChildren()) {
          toCheck.push(treeElement);
        }
      }
    }
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

  @Nullable
  private static ResourceBundleEditorViewElement getSelectedElement(@NotNull DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    if (!(userObject instanceof AbstractTreeNode)) return null;
    Object value = ((AbstractTreeNode)userObject).getValue();
    return value instanceof ResourceBundleEditorViewElement ? (ResourceBundleEditorViewElement) value : null;
  }

  private void writeEditorPropertyValue(final @Nullable String propertyName,
                                        final @NotNull Editor editor,
                                        final @NotNull PropertiesFile propertiesFile) {
    final String currentValue = editor.getDocument().getText();
    final String currentSelectedProperty = propertyName ==  null ? getSelectedPropertyName() : propertyName;
    if (currentSelectedProperty == null) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
          @Override
          public void run() {
            try {
              if (currentValue.isEmpty() &&
                  ResourceBundleEditorKeepEmptyValueToggleAction.keepEmptyProperties() &&
                  !PsiManager.getInstance(myProject).areElementsEquivalent(propertiesFile.getContainingFile(),
                                                                           myResourceBundle.getDefaultPropertiesFile().getContainingFile())) {
                myPropertiesInsertDeleteManager.deletePropertyIfExist(currentSelectedProperty, propertiesFile);
              } else {
                myPropertiesInsertDeleteManager.insertOrUpdateTranslation(currentSelectedProperty, currentValue, propertiesFile);
              }
            }
            catch (final IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    });
  }

  private void recreateEditorsPanel() {
    myValuesPanel.removeAll();
    myValuesPanel.setLayout(new CardLayout());

    if (!myProject.isOpen()) return;
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
                                                   new Insets(5, 5, 5, 5), 0, 0);
    releaseAllEditors();
    myTitledPanels.clear();
    int y = 0;
    Editor previousEditor = null;
    Editor firstEditor = null;
    for (final PropertiesFile propertiesFile : propertiesFiles) {
      final Editor editor = createEditor();
      final Editor oldEditor = myEditors.put(propertiesFile, editor);
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
      ((EditorEx) editor).addFocusListener(new FocusChangeListener() {
        @Override
        public void focusGained(final Editor editor) {
          mySelectedEditor = editor;
          final EditorEx editorEx = (EditorEx)editor;
          editorEx.setViewer(ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(propertiesFile.getVirtualFile()).hasReadonlyFiles());
        }

        @Override
        public void focusLost(final Editor eventEditor) {
          if (propertiesFile.getContainingFile().isValid()) {
            writeEditorPropertyValue(null, editor, propertiesFile);
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

      String title = propertiesFile.getName();
      title += PropertiesUtil.getPresentableLocale(propertiesFile.getLocale());
      JComponent comp = new JPanel(new BorderLayout()) {
        @Override
        public Dimension getPreferredSize() {
          Insets insets = getBorder().getBorderInsets(this);
          return new Dimension(100,editor.getLineHeight()*4+ insets.top + insets.bottom);
        }
      };
      comp.add(editor.getComponent(), BorderLayout.CENTER);
      comp.setBorder(IdeBorderFactory.createTitledBorder(title, true));
      myTitledPanels.put(propertiesFile, (JPanel)comp);

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
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        updateEditorsFromProperties(true);
      }
    });
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

  private void updateEditorsFromProperties(final boolean checkIsUnderUndoRedoAction) {
    String propertyName = getSelectedPropertyName();
    ((CardLayout)myValuesPanel.getLayout()).show(myValuesPanel, propertyName == null ? NO_PROPERTY_SELECTED : VALUES);
    if (propertyName == null) return;

    final UndoManagerImpl undoManager = (UndoManagerImpl)UndoManager.getInstance(myProject);
    for (final PropertiesFile propertiesFile : myResourceBundle.getPropertiesFiles()) {
      final EditorEx editor = (EditorEx)myEditors.get(propertiesFile);
      if (editor == null) continue;
      final IProperty property = propertiesFile.findPropertyByKey(propertyName);
      final Document document = editor.getDocument();
      CommandProcessor.getInstance().executeCommand(null, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              if (!checkIsUnderUndoRedoAction ||
                  !undoManager.isActive() ||
                  !(undoManager.isRedoInProgress() || undoManager.isUndoInProgress())) {
                updateDocumentFromPropertyValue(getPropertyEditorValue(property), document, propertiesFile);
              }
            }
          });
        }
      }, "", this);
      JPanel titledPanel = myTitledPanels.get(propertiesFile);
      ((TitledBorder)titledPanel.getBorder()).setTitleColor(property == null ? JBColor.RED : UIUtil.getLabelTextForeground());
      titledPanel.repaint();
    }
    undoManager.flushCurrentCommandMerger();
  }

  private void installPropertiesChangeListeners() {
    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    if (myVfsListener != null) {
      throw new AssertionError("Listeners can't be initialized twice");
    }
    myVfsListener = new VirtualFileAdapter() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        if (PropertiesImplUtil.isPropertiesFile(event.getFile(), myProject)) {
          recreateEditorsPanel();
        }
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        for (PropertiesFile file : myEditors.keySet()) {
          if (Comparing.equal(file.getVirtualFile(), event.getFile())) {
            recreateEditorsPanel();
            return;
          }
        }
      }

      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (PropertiesImplUtil.isPropertiesFile(event.getFile(), myProject)) {
          if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            recreateEditorsPanel();
          }
          else {
            updateEditorsFromProperties(true);
          }
        }
      }
    };

    virtualFileManager.addVirtualFileListener(myVfsListener, this);
    PsiTreeChangeAdapter psiTreeChangeAdapter = new PsiTreeChangeAdapter() {
      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        final PsiFile file = event.getFile();
        if (file instanceof XmlFile) {
          final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
          if (propertiesFile != null) {
            final ResourceBundle bundle = propertiesFile.getResourceBundle();
            if (bundle.equals(myResourceBundle) && !myEditors.containsKey(propertiesFile)) {
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
          if (bundle.equals(myResourceBundle) && myEditors.containsKey(propertiesFile)) {
            final IProperty property = PropertiesImplUtil.getProperty(event.getParent());
            if (property != null && Comparing.equal(property.getName(), getSelectedPropertyName())) {
              updateEditorsFromProperties(false);
            }
          }
        }
      }
    };
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(psiTreeChangeAdapter, this);
  }
  private void selectionChanged() {
    myBackSlashPressed.clear();
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        updateEditorsFromProperties(true);
        final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
        if (statusBar != null) {
          statusBar.setInfo("Selected property: " + getSelectedPropertyName());
        }
      }
    });
  }

  private void updateDocumentFromPropertyValue(final String value,
                                               final Document document,
                                               final PropertiesFile propertiesFile) {
    @NonNls String text = value;
    if (myBackSlashPressed.contains(propertiesFile)) {
      text += "\\";
    }
    document.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    document.replaceString(0, document.getTextLength(), text);
    document.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.FALSE);
  }

  @NotNull
  private Collection<DefaultMutableTreeNode> getSelectedNodes() {
    if (!isValid()) {
      return Collections.emptyList();
    }
    JTree tree = myStructureViewComponent.getTree();
    if (tree == null) return Collections.emptyList();
    TreePath[] selected = tree.getSelectionModel().getSelectionPaths();
    if (selected == null || selected.length == 0) return Collections.emptyList();
    return ContainerUtil.map(selected, new Function<TreePath, DefaultMutableTreeNode>() {
      @Override
      public DefaultMutableTreeNode fun(TreePath treePath) {
        return (DefaultMutableTreeNode)treePath.getLastPathComponent();
      }
    });
  }

  @Nullable
  private String getSelectedPropertyName() {
    final IProperty selectedProperty = getSelectedProperty();
    return selectedProperty == null ? null : selectedProperty.getName();
  }

  @Nullable
  private IProperty getSelectedProperty() {
    final Collection<DefaultMutableTreeNode> selectedNode = getSelectedNodes();
    if (selectedNode.isEmpty()) {
      return null;
    }
    final ResourceBundleEditorViewElement element = getSelectedElement(ContainerUtil.getFirstItem(selectedNode));
    return element instanceof ResourceBundlePropertyStructureViewElement ? ((ResourceBundlePropertyStructureViewElement)element).getProperty()
                                                                       : null;
  }

  @NotNull
  public Collection<ResourceBundleEditorViewElement> getSelectedElements() {
    final Collection<DefaultMutableTreeNode> selectedNodes = getSelectedNodes();
    return ContainerUtil.mapNotNull(selectedNodes, new NullableFunction<DefaultMutableTreeNode, ResourceBundleEditorViewElement>() {
      @Nullable
      @Override
      public ResourceBundleEditorViewElement fun(DefaultMutableTreeNode selectedNode) {
        Object userObject = selectedNode.getUserObject();
        if (!(userObject instanceof AbstractTreeNode)) return null;
        Object value = ((AbstractTreeNode)userObject).getValue();
        return value instanceof ResourceBundleEditorViewElement ? (ResourceBundleEditorViewElement) value : null;
      }
    });
  }

  @Nullable
  public ResourceBundleEditorViewElement getSelectedElementIfOnlyOne() {
    final Collection<ResourceBundleEditorViewElement> selectedElements = getSelectedElements();
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
    final List<String> keysOrder = new ArrayList<String>(propertyKeys);
    if (isAlphaSorted) {
      Collections.sort(keysOrder);
    }

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

  private Object getData(final String dataId) {
    if (SelectInContext.DATA_KEY.is(dataId)) {
      return new SelectInContext(){
        @Override
        @NotNull
        public Project getProject() {
          return myProject;
        }

        @Override
        @NotNull
        public VirtualFile getVirtualFile() {
          PropertiesFile selectedFile = getSelectedPropertiesFile();

          VirtualFile virtualFile = selectedFile == null ? null : selectedFile.getVirtualFile();
          assert virtualFile != null;
          return virtualFile;
        }

        @Override
        public Object getSelectorInFile() {
          return getSelectedPropertiesFile();
        }

        @Override
        public FileEditorProvider getFileEditorProvider() {
          final PropertiesFile selectedPropertiesFile = getSelectedPropertiesFile();
          if (selectedPropertiesFile == null) return null;
          return new FileEditorProvider() {
            @Override
            public FileEditor openFileEditor() {
              final VirtualFile file = selectedPropertiesFile.getVirtualFile();
              if (file == null) {
                return null;
              }
              return FileEditorManager.getInstance(getProject()).openFile(file, false)[0];

            }
          };
        }
      };
    }
    return null;
  }

  private PropertiesFile getSelectedPropertiesFile() {
    if (mySelectedEditor == null) return null;
    PropertiesFile selectedFile = null;
    for (Map.Entry<PropertiesFile, Editor> entry : myEditors.entrySet()) {
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
    return "Resource Bundle";
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
  public void selectNotify() {

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
    return null;
  }

  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
    if (mySelectedEditor != null) {
      for (final Map.Entry<PropertiesFile, Editor> entry : myEditors.entrySet()) {
        if (mySelectedEditor.equals(entry.getValue())) {
          writeEditorPropertyValue(null, mySelectedEditor, entry.getKey());
        }
      }
    }
    VirtualFileManager.getInstance().removeVirtualFileListener(myVfsListener);
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
  public Document[] getDocuments() {
    return ContainerUtil.map2Array(myEditors.keySet(), new Document[myEditors.size()], new Function<PropertiesFile, Document>() {
      @Override
      public Document fun(PropertiesFile propertiesFile) {
        final PsiFile file = propertiesFile.getContainingFile();
        return FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
      }
    });
  }

  public static class ResourceBundleEditorState implements FileEditorState {
    private final String myPropertyName;

    public ResourceBundleEditorState(String propertyName) {
      myPropertyName = propertyName;
    }

    @Override
    public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
      return false;
    }

    public String getPropertyName() {
      return myPropertyName;
    }
  }

  private Editor createEditor() {
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
    editor.setContextMenuGroupId(null); // disabling default context menu
    editor.addEditorMouseListener(new EditorPopupHandler() {
          @Override
          public void invokePopup(EditorMouseEvent event) {
            if (!event.isConsumed() && event.getArea() == EditorMouseEventArea.EDITING_AREA) {
              DefaultActionGroup group = new DefaultActionGroup();
              group.copyFromGroup((DefaultActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_CUT_COPY_PASTE));
              group.addSeparator();
              group.add(new AnAction("Propagate Value Across of Resource Bundle") {
                @Override
                public void actionPerformed(AnActionEvent e) {
                  final String valueToPropagate = editor.getDocument().getText();
                  final String currentSelectedProperty = getSelectedPropertyName();
                  if (currentSelectedProperty == null) {
                    return;
                  }
                  ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                      WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
                        @Override
                        public void run() {
                          try {
                            for (Map.Entry<PropertiesFile, Editor> entry : myEditors.entrySet()) {
                              final Editor translationEditor = entry.getValue();
                              if (translationEditor != editor) {
                                final PropertiesFile propertiesFile = entry.getKey();
                                myPropertiesInsertDeleteManager.insertOrUpdateTranslation(currentSelectedProperty, valueToPropagate, propertiesFile);
                                translationEditor.getDocument().setText(valueToPropagate);
                              }
                            }
                          }
                          catch (final IncorrectOperationException e) {
                            LOG.error(e);
                          }
                        }
                      });
                    }
                  });
                }
              });
              EditorPopupHandler handler = EditorActionUtil.createEditorPopupHandler(group);
              handler.invokePopup(event);
              event.consume();
            }
          }
      });
  }

  private class DataProviderPanel extends JPanel implements DataProvider {
    private DataProviderPanel(final JPanel panel) {
      super(new BorderLayout());
      add(panel, BorderLayout.CENTER);
    }

    @Override
    @Nullable
    public Object getData(String dataId) {
      return ResourceBundleEditor.this.getData(dataId);
    }
  }

  private class MyJPanel extends JPanel implements Scrollable{
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
