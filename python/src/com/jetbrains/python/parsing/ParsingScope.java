package com.jetbrains.python.parsing;

/**
 * @author vlan
 */
public class ParsingScope {
  private boolean myFunction = false;
  private boolean myClass = false;

  public ParsingScope withFunction(boolean flag) {
    final ParsingScope result = copy();
    result.myFunction = flag;
    return result;
  }

  public ParsingScope withClass(boolean flag) {
    final ParsingScope result = copy();
    result.myClass = flag;
    return result;
  }

  public boolean isFunction() {
    return myFunction;
  }

  public boolean isClass() {
    return myClass;
  }

  public boolean isSuite() {
    return myFunction || myFunction;
  }

  protected ParsingScope createInstance() {
    return new ParsingScope();
  }

  protected ParsingScope copy() {
    final ParsingScope result = createInstance();
    result.myFunction = myFunction;
    result.myClass = myClass;
    return result;
  }
}
