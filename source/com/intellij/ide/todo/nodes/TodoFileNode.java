package com.intellij.ide.todo.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.todo.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.TodoItem;
import com.intellij.ui.HighlightedRegion;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class TodoFileNode extends PsiFileNode implements HighlightedRegionProvider{
  private final TodoTreeBuilder myBuilder;
  private final ArrayList<HighlightedRegion> myHighlightedRegions;
  private final boolean mySingleFileMode;

  static private final Logger LOG = Logger.getInstance("#com.intellij.ide.todo.nodes.TodoFileNode");

  public TodoFileNode(Project project,
                      PsiFile file,
                      TodoTreeBuilder treeBuilder,
                      boolean singleFileMode){
    super(project,file,ViewSettings.DEFAULT);
    myBuilder=treeBuilder;
    myHighlightedRegions=new ArrayList<HighlightedRegion>(2);
    mySingleFileMode=singleFileMode;
  }

  public ArrayList<HighlightedRegion> getHighlightedRegions(){
    return myHighlightedRegions;
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    if (!mySingleFileMode) {
      return createGeneralList();
    } else {
      return createListForSingleFile();

    }

  }

  private Collection<AbstractTreeNode> createListForSingleFile() {
    TodoItem[] items=myBuilder.getTodoTreeStructure().getSearchHelper().findTodoItems(getValue());
    ArrayList<AbstractTreeNode> children=new ArrayList<AbstractTreeNode>(items.length);
    for (TodoItem todoItem : items) {
      Document document = PsiDocumentManager.getInstance(getProject()).getDocument(getValue());
      if (document != null && todoItem.getTextRange().getEndOffset() < document.getTextLength() + 1) {
        SmartTodoItemPointer pointer = new SmartTodoItemPointer(todoItem, document);
        TodoFilter toDoFilter = getToDoFilter();
        if (toDoFilter != null) {
          TodoItemNode itemNode = new TodoItemNode(getProject(), pointer, myBuilder);
          if (toDoFilter.contains(todoItem.getPattern())) {
            children.add(itemNode);
          }
        } else {
          children.add(new TodoItemNode(getProject(), pointer, myBuilder));
        }
      }
    }
    Collections.sort(children, SmartTodoItemPointerComparator.ourInstance);
    return children;
  }

  private Collection<AbstractTreeNode> createGeneralList() {
    ArrayList<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();

    PsiFile psiFile = getValue();
    final TodoItem[] items = myBuilder.getTodoTreeStructure().getSearchHelper().findTodoItems(psiFile);
    for (final TodoItem todoItem : items) {
      final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
      if (document != null && todoItem.getTextRange().getEndOffset() < document.getTextLength() + 1) {
        final SmartTodoItemPointer pointer = new SmartTodoItemPointer(todoItem, document);
        TodoFilter todoFilter = getToDoFilter();
        if (todoFilter != null) {
          if (todoFilter.contains(todoItem.getPattern())) {
            children.add(new TodoItemNode(getProject(), pointer, myBuilder));
          }
        } else {
          children.add(new TodoItemNode(getProject(), pointer, myBuilder));
        }
      }
    }
    Collections.sort(children, SmartTodoItemPointerComparator.ourInstance);
    return children;
  }

  private TodoFilter getToDoFilter() {
    return myBuilder.getTodoTreeStructure().getTodoFilter();
  }

  public void updateImpl(PresentationData data) {
    super.updateImpl(data);
    String newName;
    if(myBuilder.getTodoTreeStructure().isPackagesShown()){
      newName=getValue().getName();
    }else{
      newName=mySingleFileMode ? getValue().getName() : getValue().getVirtualFile().getPresentableUrl();
    }

    int nameEndOffset=newName.length();
    int todoItemCount=myBuilder.getTodoTreeStructure().getTodoItemCount(getValue());
    if(mySingleFileMode){
      if(todoItemCount==0){
        newName = IdeBundle.message("node.todo.no.items.found", newName);
      } else {
        newName = IdeBundle.message("node.todo.found.items", newName, todoItemCount);
      }
    }else{
      newName = IdeBundle.message("node.todo.items", newName, todoItemCount);
    }

    myHighlightedRegions.clear();

    TextAttributes textAttributes=new TextAttributes();
    textAttributes.setForegroundColor(myColor);
    myHighlightedRegions.add(new HighlightedRegion(0,nameEndOffset,textAttributes));

    EditorColorsScheme colorsScheme=UsageTreeColorsScheme.getInstance().getScheme();
    myHighlightedRegions.add(
      new HighlightedRegion(nameEndOffset,newName.length(),colorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES))
    );

  }
}
