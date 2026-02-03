package com.intellij.tools.apiDump.testData;

public class JPublicClassInternalSuper extends JInternalClass {

  @Override
  public JPublicClassInternalSuper clone() throws CloneNotSupportedException {
    return (JPublicClassInternalSuper)super.clone();
  }
}
