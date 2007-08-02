/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.importProject;

import com.intellij.facet.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTreeBase;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class DetectedFacetsTreeComponent {
  private static final Icon MODULE_ICON = IconLoader.getIcon("/nodes/ModuleClosed.png");
  private JPanel myMainPanel;
  private List<ModuleInfo> myModuleInfos = new ArrayList<ModuleInfo>();

  public DetectedFacetsTreeComponent() {
    myMainPanel = new JPanel(new BorderLayout());
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void addFacets(ModuleDescriptor moduleDescriptor, Map<File, List<Pair<FacetInfo, VirtualFile>>> root2Facets) {
    for (File root : root2Facets.keySet()) {
      ModuleInfo moduleInfo = new ModuleInfo(moduleDescriptor, root);

      Map<FacetInfo, DetectedFacetInfo> facetInfos = new HashMap<FacetInfo, DetectedFacetInfo>();
      for (Pair<FacetInfo, VirtualFile> pair : root2Facets.get(root)) {
        DetectedFacetInfo parent = null;
        FacetInfo underlyingFacet = pair.getFirst().getUnderlyingFacet();
        if (underlyingFacet != null) {
          parent = facetInfos.get(underlyingFacet);
        }

        String relativePath = getRelativePath(root, pair.getSecond());
        DetectedFacetInfo detectedFacet = new DetectedFacetInfo(pair.getFirst(), relativePath, parent);
        facetInfos.put(pair.getFirst(), detectedFacet);
        if (parent == null) {
          moduleInfo.addRootFacet(detectedFacet);
        }
      }

      myModuleInfos.add(moduleInfo);
    }
  }

  private static String getRelativePath(final File root, final VirtualFile file) {
    VirtualFile virtualRoot = LocalFileSystem.getInstance().findFileByIoFile(root);
    if (virtualRoot == null) return file.getPresentableUrl();
    String path = VfsUtil.getRelativePath(file, virtualRoot, File.separatorChar);
    return path != null ? path : file.getPresentableUrl();
  }

  public void createTree() {
    CheckedTreeNode root = new CheckedTreeNode(null);
    for (ModuleInfo info : myModuleInfos) {
      root.add(info);
    }
    final CheckboxTreeBase tree = new CheckboxTreeBase(new FacetsCheckboxTreeCellRenderer(), root) {
      protected void checkNode(final CheckedTreeNode node, final boolean checked) {
        adjustParentsAndChildren(node, checked);
      }
    };
    TreeUtil.expandAll(tree);
    myMainPanel.add(tree, BorderLayout.CENTER);
  }

  public void clear() {
    myMainPanel.removeAll();
    myModuleInfos.clear();
  }

  public void createFacets(final ModuleDescriptor descriptor, final Module module, final ModifiableRootModel rootModel) {
    ModifiableFacetModel modifiableModel = FacetManager.getInstance(module).createModifiableModel();
    for (ModuleInfo moduleInfo : myModuleInfos) {
      if (moduleInfo.isChecked() && moduleInfo.myModuleDescriptor.equals(descriptor)) {
        createFacets(moduleInfo.myRootFacets, module, rootModel, modifiableModel, null);
      }
    }
    modifiableModel.commit();
  }

  private static void createFacets(final List<DetectedFacetInfo> facets, final Module module, final ModifiableRootModel rootModel,
                                   final ModifiableFacetModel facetModel, Facet underlyingFacet) {
    for (DetectedFacetInfo detectedFacetInfo : facets) {
      if (!detectedFacetInfo.isChecked()) continue;

      FacetInfo facetInfo = detectedFacetInfo.myFacetInfo;
      FacetType type = facetInfo.getFacetType();
      //noinspection unchecked
      Facet facet = FacetManagerImpl.createFacet(type, module, facetInfo.getName(), facetInfo.getConfiguration(), underlyingFacet);
      facetModel.addFacet(facet);

      createFacets(detectedFacetInfo.myChildren, module, rootModel, facetModel, facet);
    }
  }

  private static class ModuleInfo extends CheckedTreeNode {
    private List<DetectedFacetInfo> myRootFacets = new ArrayList<DetectedFacetInfo>();
    private final ModuleDescriptor myModuleDescriptor;
    private final File myRoot;

    private ModuleInfo(ModuleDescriptor moduleDescriptor, final File root) {
      super(moduleDescriptor);
      myModuleDescriptor = moduleDescriptor;
      myRoot = root;
    }

    public void addRootFacet(DetectedFacetInfo facetInfo) {
      myRootFacets.add(facetInfo);
      add(facetInfo);
    }
  }

  private static class DetectedFacetInfo extends CheckedTreeNode {
    private FacetInfo myFacetInfo;
    private final String myRelativeFilePath;
    private List<DetectedFacetInfo> myChildren = new ArrayList<DetectedFacetInfo>();

    private DetectedFacetInfo(final FacetInfo facetInfo, String relativeFilePath, @Nullable DetectedFacetInfo parent) {
      super(facetInfo);
      myRelativeFilePath = relativeFilePath;
      myFacetInfo = facetInfo;
      if (parent != null) {
        parent.myChildren.add(this);
        parent.add(this);
      }
    }
  }


  private static class FacetsCheckboxTreeCellRenderer extends CheckboxTreeBase.CheckboxTreeCellRendererBase {
    public void customizeCellRenderer(final JTree tree,
                                        final Object value,
                                        final boolean selected,
                                        final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
      ColoredTreeCellRenderer renderer = getTextRenderer();
      if (value instanceof ModuleInfo) {
        ModuleInfo moduleInfo = (ModuleInfo)value;
        renderer.setIcon(MODULE_ICON);
        renderer.append(moduleInfo.myModuleDescriptor.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        renderer.append(" (" + moduleInfo.myRoot.getPath() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      else if (value instanceof DetectedFacetInfo) {
        final DetectedFacetInfo info = (DetectedFacetInfo)value;
        FacetType type = info.myFacetInfo.getFacetType();
        String title = ProjectBundle.message("checkbox.text.detected.facet.0.in.1", type.getPresentableName(), info.myRelativeFilePath);
        renderer.setIcon(type.getIcon());
        renderer.append(title, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }
}
