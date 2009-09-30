package org.intellij.lang.xpath.xslt.impl;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import org.intellij.lang.xpath.xslt.XsltConfig;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class XsltTreeStructureProvider implements TreeStructureProvider {
  private Project myProject;

  public XsltTreeStructureProvider(Project project) {
    myProject = project;
  }

  @SuppressWarnings({"RawUseOfParameterizedType", "unchecked"})
  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    Collection<AbstractTreeNode> l = children;
    int i = 0;
    for (AbstractTreeNode o : children) {
      if (o instanceof ProjectViewNode) {
        final ProjectViewNode node = (ProjectViewNode)o;
        final Object element = node.getValue();
        if (element instanceof PsiFile) {
          if (XsltSupport.isXsltFile((PsiFile)element)) {
            if (l == children && l.getClass() != ArrayList.class) {
              l = new ArrayList<AbstractTreeNode>(children);
            }
            final XsltFileNode fileNode = new XsltFileNode(myProject, (PsiFile)element, settings);
            ((List<AbstractTreeNode>)l).set(i, fileNode);
          }
        }
      }
      i++;
    }
    return l;
  }

  @Nullable
  @SuppressWarnings({"RawUseOfParameterizedType"})
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  private static class XsltFileNode extends PsiFileNode {
    private static final Icon LINK_OVERLAY = IconLoader.getIcon("/icons/association_small.png");
    private final FileAssociationsManager myInstance;
    private final XsltConfig myConfig;

    public XsltFileNode(Project project, PsiFile psiFile, ViewSettings viewSettings) {
      super(project, psiFile, viewSettings);
      myInstance = FileAssociationsManager.getInstance(myProject);
      myConfig = XsltConfig.getInstance();
    }

    public void updateImpl(PresentationData presentationData) {
      super.updateImpl(presentationData);
      final PsiFile[] psiFiles = myInstance.getAssociationsFor(getValue());

      Icon icon = XsltSupport.createXsltIcon(presentationData.getIcon(false));
      if (psiFiles.length > 0) {
        icon = LayeredIcon.create(icon, LINK_OVERLAY);
      }
      presentationData.setIcons(icon);
    }

    public boolean expandOnDoubleClick() {
      return false;
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public Collection<AbstractTreeNode> getChildrenImpl() {
      if (myConfig.isShowLinkedFiles()) {
        final PsiFile[] psiFiles = myInstance.getAssociationsFor(getValue());
        if (psiFiles.length > 0) {
          return ProjectViewNode.wrap(Arrays.asList(psiFiles), myProject, PsiFileNode.class, getSettings());
        }
      }
      return super.getChildrenImpl();
    }
  }
}
