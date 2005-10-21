package com.intellij.ui;

import java.util.ArrayList;

public class SimpleColoredText {
  private final ArrayList<String> myTexts;
  private final ArrayList<SimpleTextAttributes> myAttributes;

  public SimpleColoredText() {
    myTexts = new ArrayList<String>(3);
    myAttributes = new ArrayList<SimpleTextAttributes>(3);
  }

  public void append(String fragment,SimpleTextAttributes attributes){
    if(fragment==null){
      throw new IllegalArgumentException("fragment cannot be null");
    }
    if(attributes==null){
      throw new IllegalArgumentException("attributes cannot be null");
    }
    myTexts.add(fragment);
    myAttributes.add(attributes);
  }

  public void clear() {
    myTexts.clear();
    myAttributes.clear();
  }
  
  public void appendToComponent(SimpleColoredComponent component) {
    int size = myTexts.size();
    for (int i=0; i < size; i++){
      String text = myTexts.get(i);
      SimpleTextAttributes attribute = myAttributes.get(i);
      component.append(text, attribute);
    }
  }
}
