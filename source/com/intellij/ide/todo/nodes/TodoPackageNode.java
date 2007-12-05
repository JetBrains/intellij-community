package com.intellij.ide.todo.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
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
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.HighlightedRegion;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public final class TodoPackageNode extends PackageElementNode implements HighlightedRegionProvider {
  private final ArrayList<HighlightedRegion> myHighlightedRegions;
  private final TodoTreeBuilder myBuilder;
  @Nullable private String myPresentationName = null;

  public TodoPackageNode(Project project,
                         PackageElement element,
                         TodoTreeBuilder builder) {
    super(project, element, ViewSettings.DEFAULT);
    myBuilder = builder;
    myHighlightedRegions = new ArrayList<HighlightedRegion>(2);
    if (element != null){
      final PsiPackage aPackage = element.getPackage();
      if (aPackage != null){
        myPresentationName = aPackage.getName();
      }
    }
  }

  public TodoPackageNode(Project project,
                         PackageElement element,
                         TodoTreeBuilder builder,
                         @Nullable String name) {
    super(project, element, ViewSettings.DEFAULT);
    myBuilder = builder;
    myHighlightedRegions = new ArrayList<HighlightedRegion>(2);
    if (element != null && name == null){
      final PsiPackage aPackage = element.getPackage();
      if (aPackage != null){
        myPresentationName = aPackage.getName();
      }
    } else {
      myPresentationName = name;
    }
  }


  public ArrayList<HighlightedRegion> getHighlightedRegions() {
    return myHighlightedRegions;
  }

  protected void update(PresentationData data) {
    super.update(data);
    final PackageElement packageElement = getValue();

    if (packageElement == null || !packageElement.getPackage().isValid()) {
      setValue(null);
      return;
    }

    int fileCount = getStructure().getFileCount(packageElement);
    if (fileCount == 0){
      setValue(null);
      return;
    }

    PsiPackage aPackage = packageElement.getPackage();
    String newName;
    if (getStructure().areFlattenPackages()) {
      newName = aPackage.getQualifiedName();
    }
    else {
      newName = myPresentationName != null ? myPresentationName : "";
    }

    int nameEndOffset = newName.length();
    int todoItemCount = getStructure().getTodoItemCount(packageElement);
    newName = IdeBundle.message("node.todo.group", newName, todoItemCount, fileCount);

    myHighlightedRegions.clear();

    TextAttributes textAttributes = new TextAttributes();
    Color newColor = null;

    if (CopyPasteManager.getInstance().isCutElement(packageElement)) {
      newColor = CopyPasteManager.CUT_COLOR;
    }
    textAttributes.setForegroundColor(newColor);
    myHighlightedRegions.add(new HighlightedRegion(0, nameEndOffset, textAttributes));

    EditorColorsScheme colorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
    myHighlightedRegions.add(
      new HighlightedRegion(nameEndOffset, newName.length(), colorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES)));

    data.setPresentableText(newName);
  }

  private TodoTreeStructure getStructure() {
    return myBuilder.getTodoTreeStructure();
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    ArrayList<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final Project project = getProject();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final PsiPackage psiPackage = getValue().getPackage();
    final Module module = getValue().getModule();
    if (!getStructure().getIsFlattenPackages() || psiPackage == null) {
      final Iterator<PsiFile> iterator = myBuilder.getFiles(getValue());
      while (iterator.hasNext()) {
        final PsiFile psiFile = iterator.next();
        final Module psiFileModule = projectFileIndex.getModuleForFile(psiFile.getVirtualFile());
        //group by module
        if (module != null && psiFileModule != null && !module.equals(psiFileModule)){
          continue;
        }
        // Add files
        final PsiDirectory containingDirectory = psiFile.getContainingDirectory();
        TodoFileNode todoFileNode = new TodoFileNode(project, psiFile, myBuilder, false);
        if (ArrayUtil.find(psiPackage.getDirectories(), containingDirectory) > -1 && !children.contains(todoFileNode)) {
          children.add(todoFileNode);
          continue;
        }
        // Add packages
        PsiDirectory _dir = psiFile.getContainingDirectory();
        while (_dir != null) {
          final PsiDirectory parentDirectory = _dir.getParentDirectory();
          if (parentDirectory != null){
            PsiPackage _package = JavaDirectoryService.getInstance().getPackage(_dir);
            if (_package != null && _package.getParentPackage() != null && psiPackage.equals(_package.getParentPackage())) {
              final GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);
              _package = TodoPackageUtil.findNonEmptyPackage(_package, module, project, myBuilder, scope); //compact empty middle packages
              final String name = _package.getParentPackage().equals(psiPackage)
                                  ? null //non compacted
                                  : _package.getQualifiedName().substring(psiPackage.getQualifiedName().length() + 1);
              TodoPackageNode todoPackageNode = new TodoPackageNode(project, new PackageElement(module, _package, false), myBuilder, name);
              if (!children.contains(todoPackageNode)) {
                children.add(todoPackageNode);
                break;
              }
            }
          }
          _dir = parentDirectory;
        }
      }
      Collections.sort(children, TodoFileDirAndModuleComparator.ourInstance);
    }
    else { // flatten packages
      final Iterator<PsiFile> iterator = myBuilder.getFiles(getValue());
      while (iterator.hasNext()) {
        final PsiFile psiFile = iterator.next();
         //group by module
        final Module psiFileModule = projectFileIndex.getModuleForFile(psiFile.getVirtualFile());
        if (module != null && psiFileModule != null && !module.equals(psiFileModule)){
          continue;
        }
        final PsiDirectory _dir = psiFile.getContainingDirectory();
        // Add files
        TodoFileNode todoFileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
        if (ArrayUtil.find(psiPackage.getDirectories(), _dir) > -1 && !children.contains(todoFileNode)) {
          children.add(todoFileNode);
          continue;
        }
      }
      Collections.sort(children, TodoFileDirAndModuleComparator.ourInstance);
    }
    return children;
  }
}

