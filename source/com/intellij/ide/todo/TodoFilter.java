package com.intellij.ide.todo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TodoPattern;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
public class TodoFilter implements Cloneable{
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.todo.TodoFilter");

  private String myName;
  // TODO[vova] use array for storing TodoPatterns. Perhaps it's better...
  private HashSet myTodoPatterns;
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ELEMENT_PATTERN = "pattern";
  @NonNls private static final String ATTRIBUTE_INDEX = "index";

  /**
   * Creates filter with empty name and empty set of patterns.
   */
  public TodoFilter(){
    setName("");
    myTodoPatterns=new HashSet(1);
  }

  /**
   * @return <code>true</code> if and only if specified <code>psiFile</code> has
   * <code>TodoItem</code>s accepted by the filter.
   */
  public boolean accept(PsiSearchHelper searchHelper,PsiFile psiFile){
    for(Iterator i=iterator();i.hasNext();){
      TodoPattern todoPattern=(TodoPattern)i.next();
      if(searchHelper.getTodoItemsCount(psiFile,todoPattern)>0){
        return true;
      }
    }
    return false;
  }

  /**
   * @return filter's name. That is not <code>null</code> string.
   */
  public String getName(){
    return myName;
  }

  public void setName(String name){
    LOG.assertTrue(name!=null);
    myName=name;
  }

  /**
   * @return <code>true</code> if and only if filters contains specified <code>pattern</code>.
   */
  public boolean contains(TodoPattern pattern){
    return myTodoPatterns.contains(pattern);
  }

  /**
   * Adds specified <code>pattern</code> to the set of containing patterns.
   */
  public void addTodoPattern(TodoPattern pattern){
    LOG.assertTrue(!myTodoPatterns.contains(pattern));
    myTodoPatterns.add(pattern);
  }

  /**
   * Adds specified <code>pattern</code> from the set of containing patterns.
   */
  public void removeTodoPattern(TodoPattern pattern){
    LOG.assertTrue(myTodoPatterns.contains(pattern));
    myTodoPatterns.remove(pattern);
  }

  /**
   * @return iterator of containing patterns.
   */
  public Iterator iterator(){
    return myTodoPatterns.iterator();
  }

  /**
   * @return <code>true</code> if and only if filter contains no <code>TodoPattern</code>s.
   */
  public boolean isEmpty(){
    return myTodoPatterns.size()==0;
  }

  /**
   * @return index of specified <code>pattern</code> in the array of <code>patterns</code>.
   * Returns <code>-1</code> if <code>pattern</code> not found.
   */
  private static int getPatterIndex(TodoPattern pattern,TodoPattern[] patterns){
    for(int i=0;i<patterns.length;i++){
      if(pattern.equals(patterns[i])){
        return i;
      }
    }
    return -1;
  }

  /**
   * @param element with filter's data.
   * @param patterns all available patterns
   */
  public void readExternal(Element element,TodoPattern[] patterns) {
    myName=element.getAttributeValue(ATTRIBUTE_NAME);
    if(myName==null){
      throw new IllegalArgumentException();
    }
    myTodoPatterns.clear();
    for(Iterator i=element.getChildren().iterator();i.hasNext();){
      Element child=(Element)i.next();
      if(!ELEMENT_PATTERN.equals(child.getName())){
        continue;
      }
      try{
        int index=Integer.parseInt(child.getAttributeValue(ATTRIBUTE_INDEX));
        if(index<0||index>patterns.length-1){
          continue;
        }
        TodoPattern pattern=patterns[index];
        if(myTodoPatterns.contains(pattern)){
          continue;
        }
        myTodoPatterns.add(pattern);
      }catch(NumberFormatException exc){
        continue;
      }
    }
  }

  /**
   * @param element in which all data will be stored
   * @param patterns all available patterns
   */
  public void writeExternal(Element element,TodoPattern[] patterns){
    element.setAttribute(ATTRIBUTE_NAME,myName);
    for(Iterator i=myTodoPatterns.iterator();i.hasNext();){
      TodoPattern pattern=(TodoPattern)i.next();
      int index=getPatterIndex(pattern,patterns);
      LOG.assertTrue(index!=-1);
      Element child=new Element(ELEMENT_PATTERN);
      child.setAttribute(ATTRIBUTE_INDEX,Integer.toString(index));
      element.addContent(child);
    }
  }

  public int hashCode(){
    int hashCode=myName.hashCode();
    for(Iterator i=myTodoPatterns.iterator();i.hasNext();){
      TodoPattern todoPattern=(TodoPattern)i.next();
      hashCode+=todoPattern.hashCode();
    }
    return hashCode;
  }

  public boolean equals(Object obj){
    if(!(obj instanceof TodoFilter)){
      return false;
    }
    TodoFilter filter=(TodoFilter)obj;

    if(!myName.equals(filter.myName)){
      return false;
    }

    if(myTodoPatterns.size()!=filter.myTodoPatterns.size()){
      return false;
    }

    for(Iterator i=myTodoPatterns.iterator();i.hasNext();){
      TodoPattern pattern=(TodoPattern)i.next();
      if(!filter.contains(pattern)){
        return false;
      }
    }

    return true;
  }

  public TodoFilter clone(){
    try{
      TodoFilter filter = (TodoFilter)super.clone();
      filter.myTodoPatterns=new HashSet(myTodoPatterns);
      return filter;
    }catch(CloneNotSupportedException e){
      LOG.error(e);
      return null;
    }
  }
}