package com.intellij.ide.todo;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.todo.nodes.ModuleToDoNode;
import com.intellij.ide.todo.nodes.SummaryNode;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 */
public abstract class TodoTreeStructure extends AbstractTreeStructureBase implements ToDoSettings{
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.todo.TodoTreeStructure");

  protected TodoTreeBuilder myBuilder;
  protected AbstractTreeNode myRootElement;
  protected final ToDoSummary mySummaryElement;

  protected boolean myFlattenPackages;
  protected boolean myArePackagesShown;
  protected boolean myAreModulesShown;

  /**
   * Don't use this maps directly!
   */
  private final HashMap myElement2Children;
  private final HashMap myElement2Parent;

  protected final PsiManager myPsiManager;
  protected final PsiSearchHelper mySearchHelper;
  /**
   * Current <code>TodoFilter</code>. If no filter is set then this field is <code>null</code>.
   */
  protected TodoFilter myTodoFilter;
  private static final ArrayList<TreeStructureProvider> EMPTY_TREE_STRUCTURE_PROVIDERS = new ArrayList<TreeStructureProvider>();

  public TodoTreeStructure(Project project){
    super(project);
    myArePackagesShown=true;
    mySummaryElement=new ToDoSummary(this);
    myElement2Children=new HashMap();
    myElement2Parent=new HashMap();
    myPsiManager=PsiManager.getInstance(project);
    mySearchHelper=myPsiManager.getSearchHelper();
  }

  final void setTreeBuilder(TodoTreeBuilder builder){
    myBuilder=builder;
    myRootElement=createRootElement();
  }

  protected abstract AbstractTreeNode createRootElement();

  public abstract boolean accept(PsiFile psiFile);

  /**
   * Validate whole the cache
   */
  protected void validateCache(){
    myElement2Children.clear();
    myElement2Parent.clear();
  }

  public final boolean isPackagesShown(){
    return myArePackagesShown;
  }

  final void setShownPackages(boolean state){
    myArePackagesShown=state;
  }

  public final boolean areFlattenPackages(){
    return myFlattenPackages;
  }

  public final void setFlattenPackages(boolean state){
    myFlattenPackages=state;
  }

  /**
   * Sets new <code>TodoFilter</code>. <code>null</code> is acceptable value. It means
   * that there is no any filtration of <code>TodoItem>/code>s.
   */
  final void setTodoFilter(TodoFilter todoFilter){
    myTodoFilter=todoFilter;
  }

  /**
   * @return first element that can be selected in the tree. The method can returns <code>null</code>.
   */
  abstract Object getFirstSelectableElement();

  /**
   * @return number of <code>PsiFile</code>s located under specified element in the tree.
   */
  public final int getFileCount(Object element){
    LOG.assertTrue(element!=null);
    int count=0;
    if(element instanceof PsiFile){
      count=1;
    }else if(element instanceof PsiDirectory){
      Iterator<PsiFile> iterator = myBuilder.getFiles((PsiDirectory)element);
      while(iterator.hasNext()){
        PsiFile psiFile = iterator.next();
        if(accept(psiFile)){
          count++;
        }
      }
    }else if(element instanceof ToDoSummary){
      for(Iterator i=myBuilder.getAllFiles();i.hasNext();){
        PsiFile psiFile=(PsiFile)i.next();
        if(psiFile==null){ // skip invalid PSI files
          continue;
        }
        if(accept(psiFile)){
          count++;
        }
      }
    }else if (element instanceof Module){
      Iterator<PsiFile> iterator = myBuilder.getFiles((Module)element);
      while(iterator.hasNext()){
        PsiFile psiFile = iterator.next();
        if(accept(psiFile)){
          count++;
        }
      }
    } else if (element instanceof PackageElement){
      final PackageElement packageElement = (PackageElement)element;
      if (getIsFlattenPackages()){
        final PsiPackage aPackage = packageElement.getPackage();
        final Module module = packageElement.getModule();
        final GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(aPackage.getProject());
        final PsiDirectory[] directories = aPackage.getDirectories(scope);
        for (PsiDirectory directory : directories) {
          Iterator<PsiFile> iterator = myBuilder.getFilesUnderDirectory(directory);
          while(iterator.hasNext()){
            PsiFile psiFile = iterator.next();
            if (accept(psiFile)) count++;
          }
        }
      } else {
        Iterator<PsiFile> iterator = myBuilder.getFiles(packageElement);
        while(iterator.hasNext()){
          PsiFile psiFile = iterator.next();
          if(accept(psiFile)){
            count++;
          }
        }
      }
    }else{
      throw new IllegalArgumentException("unknown element: "+element);
    }
    return count;
  }

  /**
   * @return number of <code>TodoItem</code>s located under specified element in the tree.
   */
  public final int getTodoItemCount(Object element){
    int count=0;
    if(element instanceof PsiFile){
      PsiFile psiFile=(PsiFile)element;
      if(myTodoFilter!=null){
        for(Iterator i=myTodoFilter.iterator();i.hasNext();){
          TodoPattern pattern=(TodoPattern)i.next();
          count+=mySearchHelper.getTodoItemsCount(psiFile,pattern);
        }
      }else{
        count=mySearchHelper.getTodoItemsCount(psiFile);
      }
    }else if(element instanceof PsiDirectory){
      if (JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element)) != null){
        return 0;
      }
      Iterator<PsiFile> iterator = myBuilder.getFiles((PsiDirectory)element);
      while(iterator.hasNext()){
        PsiFile psiFile = iterator.next();
        count+=getTodoItemCount(psiFile);
      }
    }else if(element instanceof ToDoSummary){
      for(Iterator i=myBuilder.getAllFiles();i.hasNext();){
        count+=getTodoItemCount(i.next());
      }
    }else if (element instanceof Module){
      Iterator<PsiFile> iterator = myBuilder.getFiles((Module)element);
      while(iterator.hasNext()){
        PsiFile psiFile = iterator.next();
        count+=getTodoItemCount(psiFile);
      }
    } else if(element instanceof PackageElement){
      final PackageElement packageElement = (PackageElement)element;
      if (getIsFlattenPackages()){
        final PsiPackage aPackage = packageElement.getPackage();
        final Module module = packageElement.getModule();
        GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(aPackage.getProject());
        final PsiDirectory[] directories = aPackage.getDirectories(scope);
        for (PsiDirectory directory : directories) {
          Iterator<PsiFile> iterator = myBuilder.getFilesUnderDirectory(directory);
          while(iterator.hasNext()){
            PsiFile psiFile = iterator.next();
            count+=getTodoItemCount(psiFile);
          }
        }
      } else {
        Iterator<PsiFile> iterator = myBuilder.getFiles(packageElement);
        while(iterator.hasNext()){
          PsiFile psiFile = iterator.next();
          count+=getTodoItemCount(psiFile);
        }
      }
    }else{
      throw new IllegalArgumentException("unknown element: "+element);
    }
    return count;
  }

  boolean isAutoExpandNode(NodeDescriptor descriptor){
    Object element=descriptor.getElement();
    if(element==getRootElement()){
      return true;
    }else if(element==mySummaryElement){
      return true;
    }else{
      return false;
    }
  }

  boolean needLoadingNode(NodeDescriptor descriptor){
    Object element=descriptor.getElement();
    if(element == getRootElement()){
      return true;
    }else if(element instanceof SummaryNode){
      return true;
    }else if(element instanceof PsiDirectoryNode){
      return true;
    }else if(element instanceof PsiFileNode){
      return mySearchHelper.getTodoItemsCount(((PsiFileNode)element).getValue())>0;
    }else if(element instanceof TodoItemNode){
      return false;
    }else if (element instanceof ModuleToDoNode){
      return true;
    }else if (element instanceof PackageElementNode){
      return true;
    }else {
      throw new IllegalArgumentException(element.getClass().getName());
    }
  }

  public final void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  public boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  public final Object getRootElement(){
    return myRootElement;
  }

  public ToDoSettings getSettings() {
    return this;
  }

  public boolean getIsFlattenPackages() {
    return myFlattenPackages;
  }

  public PsiSearchHelper getSearchHelper() {
    return mySearchHelper;
  }

  public TodoFilter getTodoFilter() {
    return myTodoFilter;
  }

  public List<TreeStructureProvider> getProviders() {
    return EMPTY_TREE_STRUCTURE_PROVIDERS;
  }

  void setShownModules(boolean state) {
    myAreModulesShown = state;
  }

  public boolean isModulesShown() {
    return myAreModulesShown;
  }
}