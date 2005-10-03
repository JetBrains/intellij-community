/*
 * Author: max
 * Date: Oct 9, 2001
 * Time: 8:50:56 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.InspectionPackageNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.openapi.util.*;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public abstract class InspectionTool implements JDOMExternalizable {
  private InspectionManagerEx myManager;
  private JComponent myOptions;

  public InspectionTool() {
  }

  public void initialize(InspectionManagerEx manager) {
    myManager = manager;
  }

  public InspectionManagerEx getManager() {
    return myManager;
  }

  public RefManager getRefManager() {
    return myManager.getRefManager();
  }

  public abstract void runInspection(AnalysisScope scope);

  public abstract void exportResults(Element parentNode);

  @Nullable
  public QuickFixAction[] getQuickFixes(final RefElement[] refElements) {
    return null;
  }

  public abstract JobDescriptor[] getJobDescriptors();

  protected JComponent createOptionsPanel() {
    return new JPanel();
  }

  public final JComponent getOptionsPanel(boolean forceCreate) {
    if (myOptions == null || forceCreate) {
      myOptions = createOptionsPanel();
    }

    return myOptions;
  }

  public boolean queryExternalUsagesRequests() {
    return false;
  }

  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  public boolean isEnabledByDefault() {
    return getDefaultLevel() != HighlightDisplayLevel.DO_NOT_SHOW;
  }

  public abstract String getDisplayName();

  public abstract String getGroupDisplayName();

  @NonNls
  public abstract String getShortName();

  @SuppressWarnings({"HardCodedStringLiteral"})
  public final String getDescriptionFileName() {
    return getShortName() + ".html";
  }

  public final String getFolderName() {
    return getShortName();
  }

  public void cleanup() {
  }

  public abstract HTMLComposer getComposer();

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public abstract boolean hasReportedProblems();

  public abstract void updateContent();

  public abstract InspectionTreeNode[] getContents();

  public abstract Map<String, Set<RefElement>> getPackageContent();

  public abstract void ignoreElement(RefElement refElement);

  protected RefElementNode addNodeToParent(RefElement refElement, InspectionPackageNode packageNode){
    final Set<InspectionTreeNode> children = new HashSet<InspectionTreeNode>();
    TreeUtil.traverseDepth(packageNode, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        children.add((InspectionTreeNode)node);
        return true;
      }
    });
    RefElementNode nodeToAdd = new RefElementNode(refElement);
    boolean firstLevel = true;
    RefElementNode prevNode = null;
    while (true) {
      RefElementNode currentNode = firstLevel ? nodeToAdd : new RefElementNode(refElement);
      for (InspectionTreeNode node : children) {
        if (node instanceof RefElementNode){
          final RefElementNode refElementNode = (RefElementNode)node;
          if (Comparing.equal(refElementNode.getElement(), refElement)){
            if (firstLevel){
              return refElementNode;
            } else {
              refElementNode.add(prevNode);
              return nodeToAdd;
            }
          }
        }
      }
      if (!firstLevel) {
        currentNode.add(prevNode);
      }
      RefEntity owner = refElement.getOwner();
      if (!(owner instanceof RefElement)){
        packageNode.add(currentNode);
        return nodeToAdd;
      }
      refElement = (RefElement)owner;
      prevNode = currentNode;
      firstLevel = false;
    }
  }
}