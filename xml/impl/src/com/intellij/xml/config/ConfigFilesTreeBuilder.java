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
package com.intellij.xml.config;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.HashSet;

public class ConfigFilesTreeBuilder {

  private final JTree myTree;

  public ConfigFilesTreeBuilder(JTree tree) {
    myTree = tree;
    installSearch(tree);
  }

  public Set<PsiFile> buildTree(DefaultMutableTreeNode root, ConfigFileSearcher... searchers) {
    final Set<PsiFile> psiFiles = new com.intellij.util.containers.HashSet<PsiFile>();

    final MultiMap<Module, PsiFile> files = new MultiMap<Module, PsiFile>();
    final MultiMap<VirtualFile, PsiFile> jars = new MultiMap<VirtualFile, PsiFile>();
    final MultiMap<VirtualFile, PsiFile> virtualFiles = new MultiMap<VirtualFile, PsiFile>();

    for (ConfigFileSearcher searcher : searchers) {
      files.putAllValues(searcher.getFilesByModules());
      jars.putAllValues(searcher.getJars());
      virtualFiles.putAllValues(searcher.getVirtualFiles());
    }

    psiFiles.addAll(buildModuleNodes(files, jars, root));

    for (Map.Entry<VirtualFile, Collection<PsiFile>> entry : virtualFiles.entrySet()) {
      DefaultMutableTreeNode node = createFileNode(entry.getKey());
      List<PsiFile> list = new ArrayList<PsiFile>(entry.getValue());
      Collections.sort(list, FILE_COMPARATOR);
      for (PsiFile file : list) {
        node.add(createFileNode(file));
      }
      root.add(node);
    }

    return psiFiles;
  }


  public DefaultMutableTreeNode addFile(VirtualFile file) {
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTree.getModel().getRoot();
    final DefaultMutableTreeNode treeNode = createFileNode(file);
    root.add(treeNode);
    DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();
    model.nodeStructureChanged(root);

    return treeNode;
  }

  public Set<PsiFile> buildModuleNodes(final MultiMap<Module, PsiFile> files,
                                       final MultiMap<VirtualFile, PsiFile> jars,
                                       DefaultMutableTreeNode root) {

    final HashSet<PsiFile> psiFiles = new HashSet<PsiFile>();
    final List<Module> modules = new ArrayList<Module>(files.keySet());
    Collections.sort(modules, new Comparator<Module>() {
      public int compare(final Module o1, final Module o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    for (Module module : modules) {
      DefaultMutableTreeNode moduleNode = createFileNode(module);
      root.add(moduleNode);
      if (files.containsKey(module)) {
        List<PsiFile> moduleFiles = new ArrayList<PsiFile>(files.get(module));

        MultiMap<FileType, PsiFile> filesByType = new MultiMap<FileType, PsiFile>();
        for (PsiFile file : moduleFiles) {
          filesByType.putValue(file.getFileType(), file);
        }
        if (hasNonEmptyGroups(filesByType)) {
          for (Map.Entry<FileType, Collection<PsiFile>> entry : filesByType.entrySet()) {
            DefaultMutableTreeNode fileTypeNode = createFileNode(entry.getKey());
            moduleNode.add(fileTypeNode);
            addChildrenFiles(psiFiles, fileTypeNode, new ArrayList<PsiFile>(entry.getValue()));
          }
        }  else {
          addChildrenFiles(psiFiles, moduleNode, moduleFiles);
        }
      }
    }
    for (VirtualFile file : jars.keySet()) {
      final List<PsiFile> list = new ArrayList<PsiFile>(jars.get(file));
      final PsiFile jar = list.get(0).getManager().findFile(file);
      if (jar != null) {
        final DefaultMutableTreeNode jarNode = createFileNode(jar);
        root.add(jarNode);
        Collections.sort(list, FILE_COMPARATOR);
        for (PsiFile psiFile : list) {
          jarNode.add(createFileNode(psiFile));
          psiFiles.add(psiFile);
        }
      }
    }
    return psiFiles;
  }

  private static String getFileTypeNodeName(FileType key) {
    return StringUtil.capitalize(key.getDefaultExtension()) + " based context files" ;
  }

  private boolean hasNonEmptyGroups(MultiMap<FileType, PsiFile> filesByType) {
    byte nonEmptyGroups = 0;
    for (Map.Entry<FileType, Collection<PsiFile>> entry : filesByType.entrySet()) {
      Collection<PsiFile> files = entry.getValue();
      if (files != null && files.size() > 0) nonEmptyGroups++;
    }
    return nonEmptyGroups > 1;
  }

  private void addChildrenFiles(@NotNull Set<PsiFile> psiFiles, DefaultMutableTreeNode parentNode, @NotNull List<PsiFile> moduleFiles) {
    Collections.sort(moduleFiles, FILE_COMPARATOR);
    for (PsiFile file : moduleFiles) {
      final DefaultMutableTreeNode fileNode = createFileNode(file);
      parentNode.add(fileNode);
      psiFiles.add(file);
    }
  }

  protected DefaultMutableTreeNode createFileNode(Object file) {
    return new DefaultMutableTreeNode(file);
  }

  private static final Comparator<PsiFile> FILE_COMPARATOR = new Comparator<PsiFile>() {
    public int compare(final PsiFile o1, final PsiFile o2) {
      return o1.getName().compareTo(o2.getName());
    }
  };

  public static void renderNode(Object value, boolean expanded, ColoredTreeCellRenderer renderer) {
    if (!(value instanceof DefaultMutableTreeNode)) return;
    final Object object = ((DefaultMutableTreeNode)value).getUserObject();
    if (object instanceof FileType) {
      final FileType fileType = (FileType)object;
      final Icon icon = fileType.getIcon();
      renderer.setIcon(icon);
      renderer.append(getFileTypeNodeName(fileType), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } if (object instanceof Module) {
      final Module module = (Module)object;
      final Icon icon = ModuleType.get(module).getIcon();
      renderer.setIcon(icon);
      final String moduleName = module.getName();
      renderer.append(moduleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else if (object instanceof PsiFile) {
      final PsiFile psiFile = (PsiFile)object;
      final Icon icon = psiFile.getIcon(0);
      renderer.setIcon(icon);
      final String fileName = psiFile.getName();
      renderer.append(fileName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        String path = virtualFile.getPath();
        final int i = path.indexOf(JarFileSystem.JAR_SEPARATOR);
        if (i >= 0) {
          path = path.substring(i + JarFileSystem.JAR_SEPARATOR.length());
        }
        renderer.append(" (" + path + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
    else if (object instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)object;
      renderer.setIcon(VirtualFilePresentation.getIcon(file));
      renderer.append(file.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      String path = file.getPath();
      final int i = path.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (i >= 0) {
        path = path.substring(i + JarFileSystem.JAR_SEPARATOR.length());
      }
      renderer.append(" (" + path + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  public static void installSearch(JTree tree) {
    new TreeSpeedSearch(tree, new Convertor<TreePath, String>() {
      public String convert(final TreePath treePath) {
        final Object object = ((DefaultMutableTreeNode)treePath.getLastPathComponent()).getUserObject();
        if (object instanceof Module) {
          return ((Module)object).getName();
        }
        else if (object instanceof PsiFile) {
          return ((PsiFile)object).getName();
        }
        else if (object instanceof VirtualFile) {
          return ((VirtualFile)object).getName();
        }
        else {
          return "";
        }
      }
    });
  }
}
