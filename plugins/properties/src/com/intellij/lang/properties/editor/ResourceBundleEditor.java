/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class ResourceBundleEditor extends UserDataHolderBase implements FileEditor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.editor.ResourceBundleEditor");
  private JPanel myPanel;
  private JPanel myValuesPanel;
  private JPanel myStructureViewPanel;
  private JPanel mySplitParent;
  private final StructureViewComponent myStructureViewComponent;
  private final Map<PropertiesFile, Editor> myEditors;
  private final ResourceBundle myResourceBundle;
  private final Map<PropertiesFile, JPanel> myTitledPanels;
  private final JComponent myNoPropertySelectedPanel = new NoPropertySelectedPanel().getComponent();
  private final Map<Editor, DocumentListener> myDocumentListeners = new THashMap<Editor, DocumentListener>();
  private final Project myProject;
  private boolean myDisposed;
  private final DataProviderPanel myDataProviderPanel;
  // user pressed backslash in the corresponding editor.
  // we cannot store it back to properties file right now, so just append the backslash to the editor and wait for the subsequent chars
  private final Set<PropertiesFile> myBackSlashPressed = new THashSet<PropertiesFile>();
  @NonNls private static final String VALUES = "values";
  @NonNls private static final String NO_PROPERTY_SELECTED = "noPropertySelected";
  private PsiTreeChangeAdapter myPsiTreeChangeAdapter;
  @NonNls protected static final String PROPORTION_PROPERTY = "RESOURCE_BUNDLE_SPLITTER_PROPORTION";

  public ResourceBundleEditor(Project project, ResourceBundle resourceBundle) {
    myProject = project;
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);

    myResourceBundle = resourceBundle;
    myStructureViewComponent = new ResourceBundleStructureViewComponent(project, myResourceBundle, this);
    myStructureViewPanel.setLayout(new BorderLayout());
    myStructureViewPanel.add(myStructureViewComponent, BorderLayout.CENTER);

    myStructureViewComponent.getTree().getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      private String selectedPropertyName;
      private PropertiesFile selectedPropertiesFile;

      public void valueChanged(TreeSelectionEvent e) {
        // filter out temp unselect/select events
        if (getSelectedPropertyName() == null) return;
        if (!Comparing.strEqual(selectedPropertyName, getSelectedPropertyName())
            || !Comparing.equal(selectedPropertiesFile, getSelectedPropertiesFile())) {
          selectedPropertyName = getSelectedPropertyName();
          selectedPropertiesFile = getSelectedPropertiesFile();
          selectionChanged();
        }
      }
    });
    installPropertiesChangeListeners();

    myEditors = new THashMap<PropertiesFile, Editor>();
    myTitledPanels = new THashMap<PropertiesFile, JPanel>();
    recreateEditorsPanel();

    TreeElement[] children = myStructureViewComponent.getTreeModel().getRoot().getChildren();
    if (children.length != 0) {
      TreeElement child = children[0];
      String propName = ((ResourceBundlePropertyStructureViewElement)child).getValue();
      setState(new ResourceBundleEditorState(propName));
    }
    myDataProviderPanel = new DataProviderPanel(myPanel);
  }

  private Editor mySelectedEditor;
  private void recreateEditorsPanel() {
    myUpdateEditorAlarm.cancelAllRequests();

    myValuesPanel.removeAll();
    myValuesPanel.setLayout(new CardLayout());

    if (!myProject.isOpen()) return;
    JPanel valuesPanelComponent = new MyJPanel(new GridBagLayout());
    myValuesPanel.add(new JBScrollPane(valuesPanelComponent){
      @Override
      public void updateUI() {
        super.updateUI();
        getViewport().setBackground(UIManager.getColor("Panel.background"));
      }
    }, VALUES);
    myValuesPanel.add(myNoPropertySelectedPanel, NO_PROPERTY_SELECTED);

    List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(myProject);

    GridBagConstraints gc = new GridBagConstraints(0, 0, 0, 0, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                   new Insets(5, 5, 5, 5), 0, 0);
    releaseAllEditors();
    myTitledPanels.clear();
    int y = 0;
    for (final PropertiesFile propertiesFile : propertiesFiles) {
      final Editor editor = createEditor();
      myEditors.put(propertiesFile, editor);
      editor.getContentComponent().addFocusListener(new FocusAdapter() {
        public void focusGained(FocusEvent e) {
          mySelectedEditor = editor;
        }
      });
      gc.gridx = 0;
      gc.gridy = y++;
      gc.gridheight = 1;
      gc.gridwidth = GridBagConstraints.REMAINDER;
      gc.weightx = 1;
      gc.weighty = 1;
      gc.anchor = GridBagConstraints.CENTER;

      Locale locale = propertiesFile.getLocale();
      List<String> names = new ArrayList<String>();
      if (!Comparing.strEqual(locale.getDisplayLanguage(), null)) {
        names.add(locale.getDisplayLanguage());
      }
      if (!Comparing.strEqual(locale.getDisplayCountry(), null)) {
        names.add(locale.getDisplayCountry());
      }
      if (!Comparing.strEqual(locale.getDisplayVariant(), null)) {
        names.add(locale.getDisplayVariant());
      }

      String title = propertiesFile.getName();
      if (!names.isEmpty()) {
        title += " ("+StringUtil.join(names, "/")+")";
      }
      JComponent comp = new JPanel(new BorderLayout()) {
        @Override
        public Dimension getPreferredSize() {
          Insets insets = getBorder().getBorderInsets(this);
          return new Dimension(100,editor.getLineHeight()*4+ insets.top + insets.bottom);
        }
      };
      comp.add(editor.getComponent(), BorderLayout.CENTER);
      comp.setBorder(IdeBorderFactory.createTitledBorder(title));
      myTitledPanels.put(propertiesFile, (JPanel)comp);

      valuesPanelComponent.add(comp, gc);
    }

    gc.gridx = 0;
    gc.gridy = y;
    gc.gridheight = GridBagConstraints.REMAINDER;
    gc.gridwidth = GridBagConstraints.REMAINDER;
    gc.weightx = 10;
    gc.weighty = 1;

    valuesPanelComponent.add(new JPanel(), gc);
    myValuesPanel.repaint();
  }

  private void installPropertiesChangeListeners() {
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent event) {
        if (event.getFile().getFileType() == PropertiesFileType.FILE_TYPE) {
          recreateEditorsPanel();
        }
      }

      @Override
      public void fileDeleted(VirtualFileEvent event) {
        if (event.getFile().getFileType() == PropertiesFileType.FILE_TYPE) {
          recreateEditorsPanel();
        }
      }

      @Override
      public void propertyChanged(VirtualFilePropertyEvent event) {
        if (event.getFile().getFileType() == PropertiesFileType.FILE_TYPE) {
          if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            recreateEditorsPanel();
          }
          else {
            updateEditorsFromProperties();
          }
        }
      }
    }, this);
    myPsiTreeChangeAdapter = new PsiTreeChangeAdapter() {
      public void childAdded(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childRemoved(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childReplaced(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childMoved(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childrenChanged(PsiTreeChangeEvent event) {
        final PsiFile file = event.getFile();
        if (!(file instanceof PropertiesFile)) return;
        if (!((PropertiesFile)file).getResourceBundle().equals(myResourceBundle)) return;
        updateEditorsFromProperties();
      }
    };
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeAdapter, this);
  }

  private final Alarm myUpdateEditorAlarm = new Alarm();
  private void selectionChanged() {
    myBackSlashPressed.clear();
    updateEditorsFromProperties();
  }

  private void updateEditorsFromProperties() {
    myUpdateEditorAlarm.cancelAllRequests();
    myUpdateEditorAlarm.addRequest(new Runnable() {
      public void run() {
        if (!isValid()) return;
        // there is pending update which is going to change prop file anyway
        if (myUpdatePsiAlarm.getActiveRequestCount() != 0) {
          myUpdateEditorAlarm.cancelAllRequests();
          myUpdateEditorAlarm.addRequest(this, 200);
          return;
        }
        uninstallDocumentListeners();
        try {
          String propertyName = getSelectedPropertyName();
          ((CardLayout)myValuesPanel.getLayout()).show(myValuesPanel, propertyName == null ? NO_PROPERTY_SELECTED : VALUES);
          if (propertyName == null) return;

          List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(myProject);
          for (final PropertiesFile propertiesFile : propertiesFiles) {
            EditorEx editor = (EditorEx)myEditors.get(propertiesFile);
            if (editor == null) continue;
            reinitSettings(editor);
            Property property = propertiesFile.findPropertyByKey(propertyName);
            final String value = property == null ? "" : property.getValue();
            final Document document = editor.getDocument();
            CommandProcessor.getInstance().executeCommand(null, new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  public void run() {
                    updateDocumentFromPropertyValue(value, document, propertiesFile);
                  }
                });
              }
            }, "", this);

            JPanel titledPanel = myTitledPanels.get(propertiesFile);
            ((TitledBorder)titledPanel.getBorder()).setTitleColor(property == null ? Color.red : UIUtil.getLabelTextForeground());
            titledPanel.repaint();
          }
        }
        finally {
          installDocumentListeners();
        }
      }
    }, 200);
  }

  private void updateDocumentFromPropertyValue(final String value,
                                               final Document document,
                                               final PropertiesFile propertiesFile) {
    @NonNls String text = value;
    if (myBackSlashPressed.contains(propertiesFile)) {
      text += "\\";
    }
    document.replaceString(0, document.getTextLength(), text);
  }

  private void updatePropertyValueFromDocument(final String propertyName,
                                               final Project project,
                                               final PropertiesFile propertiesFile,
                                               final String text) {
    if (PropertiesUtil.isUnescapedBackSlashAtTheEnd(text)) {
      myBackSlashPressed.add(propertiesFile);
    }
    else {
      myBackSlashPressed.remove(propertiesFile);
    }
    String value = getPropertyValueFromText(text);
    Property property = propertiesFile.findPropertyByKey(propertyName);
    try {
      if (property == null) {
        property = PropertiesElementFactory.createProperty(project, propertyName, value);
        propertiesFile.addProperty(property);
      }
      else {
        property.setValue(value);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static String getPropertyValueFromText(final String text) {
    StringBuilder value = new StringBuilder();
    for (int i=0; i<text.length();i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        if (!PropertiesUtil.isUnescapedBackSlashAtTheEnd(value.toString())) {
          value.append("\\");
        }
        value.append("\n");
      }
      else {
        value.append(c);
      }
    }
    return value.toString();
  }

  private void installDocumentListeners() {
    List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(myProject);
    for (final PropertiesFile propertiesFile : propertiesFiles) {
      final EditorEx editor = (EditorEx)myEditors.get(propertiesFile);
      if (editor == null) continue;
      DocumentAdapter listener = new DocumentAdapter() {
        private String oldText;

        public void beforeDocumentChange(DocumentEvent e) {
          oldText = e.getDocument().getText();
        }

        public void documentChanged(DocumentEvent e) {
          Document document = e.getDocument();
          String text = document.getText();
          updatePropertyValueFor(document, propertiesFile, text, oldText);
        }
      };
      myDocumentListeners.put(editor, listener);
      editor.getDocument().addDocumentListener(listener);
    }
  }

  private void uninstallDocumentListeners() {
    List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(myProject);
    for (final PropertiesFile propertiesFile : propertiesFiles) {
      Editor editor = myEditors.get(propertiesFile);
      DocumentListener listener = myDocumentListeners.remove(editor);
      if (listener != null) {
        editor.getDocument().removeDocumentListener(listener);
      }
    }
  }

  private final Alarm myUpdatePsiAlarm = new Alarm();
  private void updatePropertyValueFor(final Document document, final PropertiesFile propertiesFile, final String text, final String oldText) {
    myUpdatePsiAlarm.cancelAllRequests();
    myUpdatePsiAlarm.addRequest(new Runnable() {
      public void run() {
        if (!isValid()) return;
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                Project project = propertiesFile.getProject();
                PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
                documentManager.commitDocument(document);
                Document propertiesFileDocument = documentManager.getDocument(propertiesFile);
                documentManager.commitDocument(propertiesFileDocument);

                if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
                  uninstallDocumentListeners();
                  try {
                    document.replaceString(0, document.getTextLength(), oldText);
                  }
                  finally {
                    installDocumentListeners();
                  }
                  return;
                }
                String propertyName = getSelectedPropertyName();
                if (propertyName == null) return;
                updatePropertyValueFromDocument(propertyName, project, propertiesFile, text);
              }
            });
          }
        });
      }
    }, 300, ModalityState.stateForComponent(getComponent()));
  }

  @Nullable
  private String getSelectedPropertyName() {
    JTree tree = myStructureViewComponent.getTree();
    if (tree == null) return null;
    TreePath selected = tree.getSelectionModel().getSelectionPath();
    if (selected == null) return null;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)selected.getLastPathComponent();
    Object userObject = node.getUserObject();
    if (!(userObject instanceof AbstractTreeNode)) return null;
    Object value = ((AbstractTreeNode)userObject).getValue();
    return value instanceof ResourceBundlePropertyStructureViewElement ? ((ResourceBundlePropertyStructureViewElement)value).getValue() : null;
  }

  @NotNull
  public JComponent getComponent() {
    return myDataProviderPanel;
  }

  private Object getData(final String dataId) {
    if (SelectInContext.DATA_KEY.is(dataId)) {
      return new SelectInContext(){
        @NotNull
        public Project getProject() {
          return myProject;
        }

        @NotNull
        public VirtualFile getVirtualFile() {
          PropertiesFile selectedFile = getSelectedPropertiesFile();

          VirtualFile virtualFile = selectedFile == null ? null : selectedFile.getVirtualFile();
          assert virtualFile != null;
          return virtualFile;
        }

        public Object getSelectorInFile() {
          return getSelectedPropertiesFile();
        }

        public FileEditorProvider getFileEditorProvider() {
          final PropertiesFile selectedPropertiesFile = getSelectedPropertiesFile();
          if (selectedPropertiesFile == null) return null;
          return new FileEditorProvider() {
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
    for (PropertiesFile file : myEditors.keySet()) {
      Editor editor = myEditors.get(file);
      if (editor == mySelectedEditor) {
        selectedFile = file;
        break;
      }
    }
    return selectedFile;
  }

  public JComponent getPreferredFocusedComponent() {
    return myStructureViewPanel;
  }

  @NotNull
  public String getName() {
    return "Resource Bundle";
  }

  @NotNull
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return new ResourceBundleEditorState(getSelectedPropertyName());
  }

  private Splitter getSplitter() {
    return (Splitter)mySplitParent.getComponents()[0];
  }

  public void setState(@NotNull FileEditorState state) {
    ResourceBundleEditorState myState = (ResourceBundleEditorState)state;
    String propertyName = myState.myPropertyName;
    if (propertyName != null) {
      myStructureViewComponent.select(propertyName, true);
      selectionChanged();
    }
    float proportion = 0.5f;
    try {
      String value = PropertiesComponent.getInstance(myProject).getValue(PROPORTION_PROPERTY);
      if (value != null) {
        proportion = Float.parseFloat(value);
      }
    }
    catch (NumberFormatException ignored) {
    }

    final float proportion1 = proportion;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        getSplitter().setProportion(proportion1);
      }
    });
  }

  public boolean isModified() {
    return false;
  }

  public boolean isValid() {
    return !myDisposed && !myProject.isDisposed();
  }

  public void selectNotify() {

  }

  public void deselectNotify() {

  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  public void dispose() {
    float proportion = getSplitter().getProportion();
    PropertiesComponent.getInstance(myProject).setValue(PROPORTION_PROPERTY, Double.toString(proportion));

    myDisposed = true;
    Disposer.dispose(myStructureViewComponent);
    releaseAllEditors();
  }

  private void releaseAllEditors() {
    for (Editor editor : myEditors.values()) {
      EditorFactory.getInstance().releaseEditor(editor);
    }
    myEditors.clear();
  }

  public static class ResourceBundleEditorState implements FileEditorState {
    private final String myPropertyName;

    public ResourceBundleEditorState(String propertyName) {
      myPropertyName = propertyName;
    }

    public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
      return false;
    }
  }

  private static Editor createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = editorFactory.createDocument("");
    EditorEx editor = (EditorEx)editorFactory.createEditor(document);
    reinitSettings(editor);
    return editor;
  }

  private static void reinitSettings(final EditorEx editor) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    editor.setColorsScheme(scheme);
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

    editor.setHighlighter(new LexerEditorHighlighter(new PropertiesValueHighlighter(), scheme));
    editor.setVerticalScrollbarVisible(true);
  }

  private class DataProviderPanel extends JPanel implements DataProvider {
    private DataProviderPanel(final JPanel panel) {
      super(new BorderLayout());
      add(panel, BorderLayout.CENTER);
    }

    @Nullable
    public Object getData(String dataId) {
      return ResourceBundleEditor.this.getData(dataId);
    }
  }

  private class MyJPanel extends JPanel implements Scrollable{
    private MyJPanel(LayoutManager layout) {
      super(layout);
    }

    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      Editor editor = myEditors.values().iterator().next();
      return editor.getLineHeight()*4;
    }

    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      return visibleRect.height;
    }

    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

}
