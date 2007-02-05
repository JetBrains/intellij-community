package com.intellij.ui;

import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class SimpleColoredText {
  private final ArrayList<String> myTexts;
  private final ArrayList<SimpleTextAttributes> myAttributes;
  private String myCachedToString = null;
  
  public SimpleColoredText() {
    myTexts = new ArrayList<String>(3);
    myAttributes = new ArrayList<SimpleTextAttributes>(3);
  }

  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes){
    myTexts.add(fragment);
    myCachedToString = null;
    myAttributes.add(attributes);
  }

  public void clear() {
    myTexts.clear();
    myCachedToString = null;
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

  public String toString() {
    if (myCachedToString == null) {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        for (String text : myTexts) {
          builder.append(text);
        }
        myCachedToString = builder.toString();
      }
      finally{
        StringBuilderSpinAllocator.dispose(builder);
      } 
        
    }
    return myCachedToString;
  }
}
