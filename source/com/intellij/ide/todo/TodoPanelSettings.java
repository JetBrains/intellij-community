package com.intellij.ide.todo;

import org.jdom.Element;

import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
class TodoPanelSettings{
  private boolean myArePackagesShown;
  private boolean myAreFlattenPackages;
  private boolean myIsAutoScrollToSource;
  private String myTodoFilterName;

  public void readExternal(Element e){
    for(Iterator i=e.getChildren().iterator();i.hasNext();){
      Element child=(Element)i.next();
      if("are-packages-shown".equals(child.getName())){
        myArePackagesShown=Boolean.valueOf(child.getAttributeValue("value")).booleanValue();
      }if("flatten-packages".equals(child.getName())){
        myAreFlattenPackages=Boolean.valueOf(child.getAttributeValue("value")).booleanValue();
      }else if("is-autoscroll-to-source".equals(child.getName())){
        myIsAutoScrollToSource=Boolean.valueOf(child.getAttributeValue("value")).booleanValue();
      }else if("todo-filter".equals(child.getName())){
        myTodoFilterName=child.getAttributeValue("name");
      }
    }
  }

  public void writeExternal(Element e){
    Element areArePackagesShownElement=new Element("are-packages-shown");
    areArePackagesShownElement.setAttribute("value",myArePackagesShown?Boolean.TRUE.toString():Boolean.FALSE.toString());
    e.addContent(areArePackagesShownElement);

    Element areAreFlattenPackagesElement=new Element("flatten-packages");
    areAreFlattenPackagesElement.setAttribute("value",myAreFlattenPackages?Boolean.TRUE.toString():Boolean.FALSE.toString());
    e.addContent(areAreFlattenPackagesElement);

    Element isAutoScrollModeElement=new Element("is-autoscroll-to-source");
    isAutoScrollModeElement.setAttribute("value",myIsAutoScrollToSource?Boolean.TRUE.toString():Boolean.FALSE.toString());
    e.addContent(isAutoScrollModeElement);

    if(myTodoFilterName!=null){
      Element todoFilterElement=new Element("todo-filter");
      todoFilterElement.setAttribute("name",myTodoFilterName);
      e.addContent(todoFilterElement);
    }
  }

  boolean arePackagesShown(){
    return myArePackagesShown;
  }

  public void setShownPackages(boolean state){
    myArePackagesShown=state;
  }

  boolean areFlattenPackages(){
    return myAreFlattenPackages;
  }

  void setAreFlattenPackages(boolean state){
    myAreFlattenPackages=state;
  }

  boolean isAutoScrollToSource(){
    return myIsAutoScrollToSource;
  }

  void setAutoScrollToSource(boolean state){
    myIsAutoScrollToSource=state;
  }

  String getTodoFilterName(){
    return myTodoFilterName;
  }

  void setTodoFilterName(String todoFilterName){
    myTodoFilterName=todoFilterName;
  }
}
