package com.intellij.xml.util.documentation;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2004
 * Time: 23:53:29
 * To change this template use File | Settings | File Templates.
 */
class EntityDescriptor {
  private String description;
  private String helpRef;
  private String name;
  private char dtd;

  static final char LOOSE_DTD = 'L';
  static final char FRAME_DTD = 'D';

  char getDtd() {
    return dtd;
  }

  void setDtd(char dtd) {
    this.dtd = dtd;
  }

  String getDescription() {
    return description;
  }

  void setDescription(String description) {
    this.description = description;
  }

  String getHelpRef() {
    return helpRef;
  }

  void setHelpRef(String helpRef) {
    this.helpRef = helpRef;
  }

  String getName() {
    return name;
  }

  void setName(String name) {
    this.name = name;
  }
}
