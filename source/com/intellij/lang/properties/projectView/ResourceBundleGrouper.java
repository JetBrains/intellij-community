package com.intellij.lang.properties.projectView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.psi.PsiElement;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ResourceBundleGrouper implements TreeStructureProvider, ProjectComponent {
  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    if (parent instanceof ResourceBundleNode) return children;

    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    Set<ResourceBundle> resourceBundles = new THashSet<ResourceBundle>();

    for (final AbstractTreeNode child : children) {
      Object f = child.getValue();
      if (f instanceof PropertiesFile) {
        PropertiesFile propertiesFile = (PropertiesFile)f;
        ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
        if (resourceBundle.getPropertiesFiles().size() != 1) {
          if (resourceBundles.add(resourceBundle)) {
            result.add(new ResourceBundleNode(propertiesFile.getProject(), resourceBundle, settings));
          }
          continue;
        }
      }
      result.add(child);
    }
    return result;
  }

  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    if (selected == null) return null;
    for (AbstractTreeNode selectedElement : selected) {
      Object element = selectedElement.getValue();
      if (DataConstantsEx.VIRTUAL_FILE.equals(dataName)) {
        if (element instanceof ResourceBundle) {
          return new ResourceBundleAsVirtualFile((ResourceBundle)element);
        }
      }
      if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataName)) {
        if (element instanceof ResourceBundle) {
          return ((ResourceBundle)element).getPropertiesFiles().toArray(new PsiElement[0]);
        }
      }
      if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataName)) {
        if (element instanceof ResourceBundle) {
          return new ResourceBundleDeleteProvider((ResourceBundle)element);
        }
      }
    }
    return null;
  }

  public String getComponentName() {
    return "ResourceBundleGrouper";
  }

  public void initComponent() {
  }

  public void disposeComponent() {

  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

}
