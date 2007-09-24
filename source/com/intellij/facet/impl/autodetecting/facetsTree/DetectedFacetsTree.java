/*
 * Copyright (c) 2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting.facetsTree;

import com.intellij.facet.FacetType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.CheckboxTreeBase;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
*/
public class DetectedFacetsTree extends CheckboxTreeBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.autodetecting.facetsTree.DetectedFacetsTree");
  private static final Icon MODULE_ICON = IconLoader.getIcon("/nodes/ModuleClosed.png");

  public DetectedFacetsTree(final Collection<? extends CheckedTreeNode> roots) {
    super(new FacetsCheckboxTreeCellRenderer(), createRoot(roots));
  }

  private static CheckedTreeNode createRoot(final Collection<? extends CheckedTreeNode> nodes) {
    CheckedTreeNode root = new CheckedTreeNode(null);
    for (CheckedTreeNode node : nodes) {
      if (!(node instanceof FacetTypeNode) && !(node instanceof ModuleNode) && !(node instanceof FacetNode)) {
        LOG.error("incorrect node class: " + node.getClass().getName());

      }
      root.add(node);
    }
    return root;
  }

  protected void checkNode(final CheckedTreeNode node, final boolean checked) {
    adjustParentsAndChildren(node, checked);
  }

  private static class FacetsCheckboxTreeCellRenderer extends CheckboxTreeCellRendererBase {
    public void customizeCellRenderer(final JTree tree,
                                        final Object value,
                                        final boolean selected,
                                        final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
      ColoredTreeCellRenderer renderer = getTextRenderer();
      if (value instanceof ModuleNode) {
        ModuleNode moduleNode = (ModuleNode)value;
        renderer.setIcon(MODULE_ICON);
        renderer.append(moduleNode.getModuleName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        renderer.append(moduleNode.getModuleDescription(), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      else if (value instanceof FacetNode) {
        final FacetNode node = (FacetNode)value;
        FacetType type = node.getFacetType();
        renderer.setIcon(type.getIcon());
        renderer.append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        renderer.append(" (" + node.myRelativeFilePath + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      else if (value instanceof FacetTypeNode) {
        FacetType type = ((FacetTypeNode)value).getFacetType();
        renderer.setIcon(type.getIcon());
        renderer.append(ProjectBundle.message("detected.facet.type.node", type.getPresentableName()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }

  }

  public static abstract class ModuleNode extends CheckedTreeNode{
    private List<FacetNode> myRootFacets = new ArrayList<FacetNode>();

    public ModuleNode(Object userObject) {
      super(userObject);
    }


    public abstract String getModuleName();

    public abstract String getModuleDescription();

    public void addRootFacet(FacetNode facetInfo) {
      myRootFacets.add(facetInfo);
      add(facetInfo);
    }

    public List<FacetNode> getRootFacets() {
      return myRootFacets;
    }
  }

  public static class FacetTypeNode extends CheckedTreeNode {
    private FacetType myFacetType;
    private List<ModuleNode> myModuleNodes = new ArrayList<ModuleNode>();

    public FacetTypeNode(final FacetType facetType) {
      super(facetType);
      myFacetType = facetType;
    }

    public FacetType getFacetType() {
      return myFacetType;
    }

    public void addModuleNode(ModuleNode moduleNode) {
      myModuleNodes.add(moduleNode);
      add(moduleNode);
    }

    public List<ModuleNode> getModuleNodes() {
      return myModuleNodes;
    }
  }

  public static class FacetNode extends CheckedTreeNode {
    private final VirtualFile myFile;
    private final FacetType myFacetType;
    private final String myRelativeFilePath;
    private List<FacetNode> myChildren = new ArrayList<FacetNode>();

    public FacetNode(Object userObject, FacetType facetType, String relativeFilePath, final VirtualFile file, @Nullable FacetNode parent) {
      super(userObject);
      myFacetType = facetType;
      myRelativeFilePath = relativeFilePath;
      myFile = file;
      if (parent != null) {
        parent.myChildren.add(this);
        parent.add(this);
      }
    }

    public String getName() {
      return myFacetType.getPresentableName();
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public List<FacetNode> getChildren() {
      return myChildren;
    }

    public FacetType getFacetType() {
      return myFacetType;
    }
  }
}
