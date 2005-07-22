package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.HighlightedRegionProvider;
import com.intellij.ide.todo.TodoFileDirAndModuleComparator;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HighlightedRegion;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class ModuleToDoNode extends BaseToDoNode<Module> implements HighlightedRegionProvider {
  private ArrayList myHighlightedRegions;

  public ModuleToDoNode(Project project, Module value, TodoTreeBuilder builder) {
    super(project, value, builder);
    myHighlightedRegions = new ArrayList(2);
  }

  public Collection<AbstractTreeNode> getChildren() {
    ArrayList<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    if (myToDoSettings.getIsPackagesShown()) {
      TodoPackageUtil.addPackagesToChildren(children, getValue(), myBuilder, getProject());
    }
    else {
      for (Iterator i = myBuilder.getAllFiles(); i.hasNext();) {
        final PsiFile psiFile = (PsiFile)i.next();
        if (psiFile == null) { // skip invalid PSI files
          continue;
        }
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        final boolean isInContent = ModuleRootManager.getInstance(getValue()).getFileIndex().isInContent(virtualFile);
        if (!isInContent) continue;
        TodoFileNode fileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
        if (getTreeStructure().accept(psiFile) && !children.contains(fileNode)) {
          children.add(fileNode);
        }
      }
    }
    Collections.sort(children, TodoFileDirAndModuleComparator.ourInstance);
    return children;

  }

  private TodoTreeStructure getStructure() {
    return myBuilder.getTodoTreeStructure();
  }

  public void update(PresentationData presentation) {
    String newName = getValue().getName();
    StringBuffer sb = new StringBuffer(newName);
    int nameEndOffset = newName.length();
    int todoItemCount = getStructure().getTodoItemCount(getValue());
    sb.append(" (").append(todoItemCount).append(" item");
    if (todoItemCount != 1) {
      sb.append('s');
    }
    int fileCount = getStructure().getFileCount(getValue());
    sb.append(" in ").append(fileCount).append(" file");
    if (fileCount != 1) {
      sb.append('s');
    }
    sb.append(')');
    newName = sb.toString();
    myHighlightedRegions.clear();

    TextAttributes textAttributes = new TextAttributes();

    if (CopyPasteManager.getInstance().isCutElement(getValue())) {
      textAttributes.setForegroundColor(CopyPasteManager.CUT_COLOR);
    }
    myHighlightedRegions.add(new HighlightedRegion(0, nameEndOffset, textAttributes));

    EditorColorsScheme colorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
    myHighlightedRegions.add(
      new HighlightedRegion(nameEndOffset, newName.length(), colorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES)));
    presentation.setOpenIcon(getValue().getModuleType().getNodeIcon(true));
    presentation.setClosedIcon(getValue().getModuleType().getNodeIcon(false));
    presentation.setPresentableText(newName);
  }

  public String getTestPresentation() {
    return "Module";
  }

  public ArrayList getHighlightedRegions() {
    return myHighlightedRegions;
  }
}
