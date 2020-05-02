package org.jetbrains.plugins.textmate.language.syntax;

public class InjectionNodeDescriptor {
  private final String mySelector;
  private final SyntaxNodeDescriptor mySyntaxNodeDescriptor;

  public InjectionNodeDescriptor(String selector, SyntaxNodeDescriptor syntaxNodeDescriptor) {
    mySelector = selector;
    mySyntaxNodeDescriptor = syntaxNodeDescriptor;
  }

  public String getSelector() {
    return mySelector;
  }

  public SyntaxNodeDescriptor getSyntaxNodeDescriptor() {
    return mySyntaxNodeDescriptor;
  }
}
