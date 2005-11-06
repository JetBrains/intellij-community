/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public class AddChildInvocation implements Invocation{
  private Class myClass;
  private String myTagName;
  private int myStartIndex;

  public AddChildInvocation(final Class aClass, final String tagName, final int startIndex) {
    myClass = aClass;
    myTagName = tagName;
    myStartIndex = startIndex;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    int index = args.length == 0 ? Integer.MAX_VALUE : myStartIndex + (Integer)args[0];
    return handler.addChild(myTagName, myClass, index);
  }
}
