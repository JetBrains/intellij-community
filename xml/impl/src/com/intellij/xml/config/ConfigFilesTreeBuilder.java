// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.config;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.containers.MultiMap;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

public class ConfigFilesTreeBuilder {

  private final JTree myTree;

  public ConfigFilesTreeBuilder(JTree tree) {
    myTree = tree;
    installSearch(tree);
  }

  public Set<PsiFile> buildTree(DefaultMutableTreeNode root, ConfigFileSearcher... searchers) {

    final MultiMap<Module, PsiFile> files = new MultiMap<>();
    final MultiMap<VirtualFile, PsiFile> jars = new MultiMap<>();
    final MultiMap<VirtualFile, PsiFile> virtualFiles = new MultiMap<>();

    for (ConfigFileSearcher searcher : searchers) {
      files.putAllValues(searcher.getFilesByModules());
      jars.putAllValues(searcher.getJars());
      virtualFiles.putAllValues(searcher.getVirtualFiles());
    }

    final Set<PsiFile> psiFiles = new HashSet<>(buildModuleNodes(files, jars, root));

    for (Map.Entry<VirtualFile, Collection<PsiFile>> entry : virtualFiles.entrySet()) {
      DefaultMutableTreeNode node = createFileNode(entry.getKey());
      List<PsiFile> list = new ArrayList<>(entry.getValue());
      list.sort(FILE_COMPARATOR);
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

    final HashSet<PsiFile> psiFiles = new HashSet<>();
    final List<Module> modules = new ArrayList<>(files.keySet());
    modules.sort(ModulesAlphaComparator.INSTANCE);
    for (Module module : modules) {
      DefaultMutableTreeNode moduleNode = createFileNode(module);
      root.add(moduleNode);
      if (files.containsKey(module)) {
        List<PsiFile> moduleFiles = new ArrayList<>(files.get(module));

        MultiMap<FileType, PsiFile> filesByType = new MultiMap<>();
        for (PsiFile file : moduleFiles) {
          filesByType.putValue(file.getFileType(), file);
        }
        if (hasNonEmptyGroups(filesByType)) {
          for (Map.Entry<FileType, Collection<PsiFile>> entry : filesByType.entrySet()) {
            DefaultMutableTreeNode fileTypeNode = createFileNode(entry.getKey());
            moduleNode.add(fileTypeNode);
            addChildrenFiles(psiFiles, fileTypeNode, new ArrayList<>(entry.getValue()));
          }
        }
        else {
          addChildrenFiles(psiFiles, moduleNode, moduleFiles);
        }
      }
    }

    List<VirtualFile> sortedJars = new ArrayList<>(jars.keySet());
    sortedJars.sort((o1, o2) -> StringUtil.naturalCompare(o1.getName(), o2.getName()));
    for (VirtualFile file : sortedJars) {
      if (!file.isValid()) continue;
      final List<PsiFile> list = new ArrayList<>(jars.get(file));
      final PsiFile jar = list.get(0).getManager().findFile(file);
      if (jar != null) {
        final DefaultMutableTreeNode jarNode = createFileNode(jar);
        root.add(jarNode);
        list.sort(FILE_COMPARATOR);
        for (PsiFile psiFile : list) {
          jarNode.add(createFileNode(psiFile));
          psiFiles.add(psiFile);
        }
      }
    }
    return psiFiles;
  }

  @Nls
  private static String getFileTypeNodeName(FileType fileType) {
    return XmlBundle.message("xml.tree.config.files.type", fileType.getName());
  }

  private static boolean hasNonEmptyGroups(MultiMap<FileType, PsiFile> filesByType) {
    long nonEmptyGroups = filesByType.entrySet().stream().map(Map.Entry::getValue)
      .filter(files -> files != null && !files.isEmpty()).limit(2).count();
    return nonEmptyGroups > 1;
  }

  private void addChildrenFiles(@NotNull Set<? super PsiFile> psiFiles, DefaultMutableTreeNode parentNode, @NotNull List<? extends PsiFile> moduleFiles) {
    moduleFiles.sort(FILE_COMPARATOR);
    for (PsiFile file : moduleFiles) {
      final DefaultMutableTreeNode fileNode = createFileNode(file);
      parentNode.add(fileNode);
      psiFiles.add(file);
    }
  }

  protected DefaultMutableTreeNode createFileNode(Object file) {
    return new DefaultMutableTreeNode(file);
  }

  private static final Comparator<PsiFile> FILE_COMPARATOR = (o1, o2) -> StringUtil.naturalCompare(o1.getName(), o2.getName());

  public static void renderNode(Object value, boolean expanded, ColoredTreeCellRenderer renderer) {
    if (!(value instanceof DefaultMutableTreeNode)) return;
    final Object object = ((DefaultMutableTreeNode)value).getUserObject();
    if (object instanceof FileType) {
      final FileType fileType = (FileType)object;
      final Icon icon = fileType.getIcon();
      renderer.setIcon(icon);
      renderer.append(getFileTypeNodeName(fileType), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else if (object instanceof Module) {
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
        renderPath(renderer, virtualFile);
      }
    }
    else if (object instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)object;
      renderer.setIcon(VirtualFilePresentation.getIcon(file));
      renderer.append(file.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      renderPath(renderer, file);
    }
  }

  private static void renderPath(ColoredTreeCellRenderer renderer, VirtualFile virtualFile) {
    String path = virtualFile.getPath(); //NON-NLS
    final int i = path.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (i >= 0) {
      path = path.substring(i + JarFileSystem.JAR_SEPARATOR.length());
    }
    renderer.append(" (" + StringUtil.trimEnd(StringUtil.trimEnd(path, virtualFile.getName()), "/") + ")",
                    SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  public static void installSearch(JTree tree) {
    new TreeSpeedSearch(tree, treePath -> {
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
    });
  }
}
