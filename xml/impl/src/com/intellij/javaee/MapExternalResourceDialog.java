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
package com.intellij.javaee;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xml.config.ConfigFileSearcher;
import com.intellij.xml.config.ConfigFilesTreeBuilder;
import com.intellij.xml.index.IndexedRelevantResource;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.index.XsdNamespaceBuilder;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class MapExternalResourceDialog extends DialogWrapper {

  private static final FileChooserDescriptor FILE_CHOOSER_DESCRIPTOR = new FileChooserDescriptor(true, false, false, false, true, false).withTitle("Choose Schema File");

  private JTextField myUri;
  private JPanel myMainPanel;
  private JTree mySchemasTree;
  private JPanel mySchemasPanel;
  private TextFieldWithBrowseButton myFileTextField;
  private boolean mySchemaFound;

  public MapExternalResourceDialog(String uri, @Nullable Project project, @Nullable PsiFile file, @Nullable String location) {
    super(project);
    setTitle("Map External Resource");
    myUri.setText(uri);
    myUri.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validateInput();
      }
    });

    if (project != null) {
      String path = project.getBasePath();
      if (path != null) {
        myFileTextField.setText(FileUtil.toSystemDependentName(path));
      }
      setupSchemasTree(uri, project, file, location);
    }
    else {
      mySchemasPanel.setVisible(false);
    }
    myFileTextField.addBrowseFolderListener(new TextBrowseFolderListener(FILE_CHOOSER_DESCRIPTOR, project));
    init();
  }

  private void setupSchemasTree(String uri,
                                @NotNull Project project,
                                @Nullable PsiFile file,
                                @Nullable String location) {

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    mySchemasTree.setModel(new DefaultTreeModel(root));
    ConfigFileSearcher searcher = new ConfigFileSearcher(file == null ? null : ModuleUtilCore.findModuleForPsiElement(file), project) {
      @Override
      public Set<PsiFile> search(@Nullable Module module, @NotNull Project project) {
        List<IndexedRelevantResource<String, XsdNamespaceBuilder>> resources = XmlNamespaceIndex.getAllResources(module, project, null);

        HashSet<PsiFile> files = new HashSet<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        for (IndexedRelevantResource<String, XsdNamespaceBuilder> resource : resources) {
          VirtualFile file = resource.getFile();
          PsiFile psiFile = psiManager.findFile(file);
          ContainerUtil.addIfNotNull(files, psiFile);
        }
        return files;
      }
    };
    searcher.search();
    new ConfigFilesTreeBuilder(mySchemasTree).buildTree(root, searcher);
    TreeUtil.expandAll(mySchemasTree);
    mySchemasTree.setRootVisible(false);
    mySchemasTree.setShowsRootHandles(true);

    ColoredTreeCellRenderer renderer = new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        ConfigFilesTreeBuilder.renderNode(value, expanded, this);
      }
    };
    renderer.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));

    mySchemasTree.setCellRenderer(renderer);
    MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() > 1 && isOKActionEnabled()) {
          doOKAction();
        }
      }
    };
    mySchemasTree.addMouseListener(mouseAdapter);
    mySchemasTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        validateInput();
      }
    });

    mySchemasTree.setSelectionRow(0);
    PsiFile schema = null;
    if (file != null) {
      schema = XmlUtil.findNamespaceByLocation(file, uri);
    }
    else if (location != null) {
      VirtualFile virtualFile = VfsUtilCore.findRelativeFile(location, null);
      if (virtualFile != null) {
        schema = PsiManager.getInstance(project).findFile(virtualFile);
      }
    }

    if (schema != null) {
      DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, schema);
      if (node != null) {
        mySchemaFound = true;
        TreeUtil.selectNode(mySchemasTree, node);
      }
      myFileTextField.setText(schema.getVirtualFile().getCanonicalPath());
    }
  }

  private void validateInput() {
    setOKActionEnabled(!StringUtil.isEmpty(myUri.getText()) && getResourceLocation() != null);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return StringUtil.isEmpty(myUri.getText()) ? myUri : mySchemaFound ? mySchemasTree : myFileTextField.getTextField();
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(400, 300);
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  public String getUri() {
    return myUri.getText();
  }

  @Nullable
  public String getResourceLocation() {
    if (mySchemasTree.hasFocus()) {
      TreePath path = mySchemasTree.getSelectionPath();
      if (path == null) return null;
      Object object = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
      if (!(object instanceof PsiFile)) return null;
      return FileUtil.toSystemIndependentName(((PsiFile)object).getVirtualFile().getPath());
    }
    else {
      return myFileTextField.getText();
    }
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "Map External Resource dialog";
  }

  private void createUIComponents() {
    FileTextField field = FileChooserFactory.getInstance().createFileTextField(FILE_CHOOSER_DESCRIPTOR, getDisposable());
    myFileTextField = new TextFieldWithBrowseButton(field.getField());
  }
}
