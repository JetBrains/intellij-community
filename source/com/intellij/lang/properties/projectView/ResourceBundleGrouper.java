package com.intellij.lang.properties.projectView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.ResourceBundleAsVirtualFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ResourceBundleGrouper implements TreeStructureProvider, ProjectComponent {
  private final Project myProject;

  public ResourceBundleGrouper(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    if (parent instanceof ResourceBundleNode) return children;

    Map<ResourceBundle,Collection<PropertiesFile>> childBundles = new THashMap<ResourceBundle, Collection<PropertiesFile>>();
    for (AbstractTreeNode child : children) {
      Object f = child.getValue();
      if (f instanceof PropertiesFile) {
        PropertiesFile propertiesFile = (PropertiesFile)f;
        ResourceBundle bundle = propertiesFile.getResourceBundle();
        Collection<PropertiesFile> files = childBundles.get(bundle);
        if (files == null) {
          files = new SmartList<PropertiesFile>();
          childBundles.put(bundle, files);
        }
        files.add(propertiesFile);
      }
    }

    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (Map.Entry<ResourceBundle, Collection<PropertiesFile>> entry : childBundles.entrySet()) {
      ResourceBundle resourceBundle = entry.getKey();
      Collection<PropertiesFile> files = entry.getValue();
      if (files.size() != 1) {
        result.add(new ResourceBundleNode(myProject, resourceBundle, settings));
      }
    }
    for (AbstractTreeNode child : children) {
      Object f = child.getValue();
      if (f instanceof PropertiesFile) {
        PropertiesFile propertiesFile = (PropertiesFile)f;
        ResourceBundle bundle = propertiesFile.getResourceBundle();
        if (childBundles.get(bundle).size() != 1) {
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
      if (DataConstants.VIRTUAL_FILE.equals(dataName)) {
        if (element instanceof ResourceBundle) {
          return new ResourceBundleAsVirtualFile((ResourceBundle)element);
        }
      }
      if (DataConstants.PSI_ELEMENT_ARRAY.equals(dataName)) {
        if (element instanceof ResourceBundle) {
          List<PropertiesFile> propertiesFiles = ((ResourceBundle)element).getPropertiesFiles(myProject);
          return propertiesFiles.toArray(new PsiElement[0]);
        }
      }
      if (DataConstants.DELETE_ELEMENT_PROVIDER.equals(dataName)) {
        if (element instanceof ResourceBundle) {
          return new ResourceBundleDeleteProvider((ResourceBundle)element);
        }
      }
    }
    return null;
  }

  public PsiElement getTopLevelElement(final PsiElement element) {
    return null;
  }

  @NotNull
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
