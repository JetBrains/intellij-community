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
package com.intellij.javaee;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTabbedPane;
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
 *         Date: 7/17/12
 */
public class MapExternalResourceDialog extends DialogWrapper {

  private JTextField myUri;
  private JPanel myMainPanel;
  private JTree mySchemasTree;
  private JPanel myExplorerPanel;
  private JBTabbedPane myTabs;
  private final FileSystemTreeImpl myExplorer;
  private String myLocation;


  public MapExternalResourceDialog(String uri, @NotNull Project project, @Nullable PsiFile file, @Nullable String location) {
    super(project);
    setTitle("Map External Resource");
    myUri.setText(uri);

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    mySchemasTree.setModel(new DefaultTreeModel(root));
    ConfigFileSearcher searcher = new ConfigFileSearcher(file == null ? null : ModuleUtilCore.findModuleForPsiElement(file), project) {
      @Override
      public Set<PsiFile> search(@Nullable Module module, @NotNull Project project) {
        List<IndexedRelevantResource<String, XsdNamespaceBuilder>> resources = XmlNamespaceIndex.getAllResources(module, project, null);

        HashSet<PsiFile> files = new HashSet<PsiFile>();
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
    new ConfigFilesTreeBuilder(mySchemasTree).buildTree(searcher, root);
    TreeUtil.expandAll(mySchemasTree);
    mySchemasTree.setRootVisible(false);
    mySchemasTree.setShowsRootHandles(true);

    ColoredTreeCellRenderer renderer = new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(JTree tree,
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

    myUri.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validateInput();
      }
    });
    mySchemasTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        validateInput();
      }
    });

    myExplorer = new FileSystemTreeImpl(project, new FileChooserDescriptor(true, false, false, false, true, false));

    myExplorer.addListener(new FileSystemTree.Listener() {
      @Override
      public void selectionChanged(List<VirtualFile> selection) {
        validateInput();
      }
    }, myExplorer);
    myExplorer.getTree().addMouseListener(mouseAdapter);

    myExplorerPanel.add(ScrollPaneFactory.createScrollPane(myExplorer.getTree()), BorderLayout.CENTER);

    PsiFile schema = null;
    if (file != null) {
      schema = XmlUtil.findNamespaceByLocation(file, uri);
    }
    else if (location != null) {
      VirtualFile virtualFile = VfsUtil.findRelativeFile(location, null);
      if (virtualFile != null) {
        schema = PsiManager.getInstance(project).findFile(virtualFile);
      }
    }

    if (schema != null) {
      DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, schema);
      if (node != null) {
        TreeUtil.selectNode(mySchemasTree, node);
      }
      myExplorer.select(schema.getVirtualFile(), null);
    }

    init();
  }

  @Override
  protected void processDoNotAskOnOk(int exitCode) {
    super.processDoNotAskOnOk(exitCode);
    // store it since explorer will be disposed
    myLocation = getResourceLocation();
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
    return StringUtil.isEmpty(myUri.getText()) ? myUri : mySchemasTree;
  }

  public String getUri() {
    return myUri.getText();
  }

  @Nullable
  public String getResourceLocation() {
    if (myLocation != null) return myLocation;

    if (myTabs.getSelectedIndex() == 0) {
      TreePath path = mySchemasTree.getSelectionPath();
      if (path == null) return null;
      Object object = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
      if (!(object instanceof PsiFile)) return null;
      return FileUtil.toSystemIndependentName(((PsiFile)object).getVirtualFile().getPath());
    }
    else {
      VirtualFile file = myExplorer.getSelectedFile();
      return file == null ? null : FileUtil.toSystemIndependentName(file.getPath());
    }
  }
}
