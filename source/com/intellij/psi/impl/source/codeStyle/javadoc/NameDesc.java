package com.intellij.psi.impl.source.codeStyle.javadoc;

/**
*
* @author Dmitry Skavish
*/
public class NameDesc {

  public String name;
  public String desc;
  private String type;

  public NameDesc(String name, String desc) {
    this.name = name;
    this.desc = desc;
  }

  public NameDesc(String name, String desc, String type) {
    this.name = name;
    this.desc = desc;
    this.type = type;
  }

  public String toString() {
    if (type == null) return name;
    return name + ": " + type;
  }
}
