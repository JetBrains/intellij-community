package com.intellij.ide.todo;

/**
 * @author Vladimir Kondratyev
 */
public class ToDoSummary{
  private TodoTreeStructure myStructure;

  public ToDoSummary(TodoTreeStructure structure){
    myStructure=structure;
  }

  public int getFileCount(){
    return myStructure.getFileCount(this);
  }

  public int getTodoItemCount(){
    return myStructure.getTodoItemCount(this);
  }
}
