package com.intellij.xml.util.documentation;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2004
 * Time: 23:54:27
 * To change this template use File | Settings | File Templates.
 */
class HtmlTagDescriptor extends EntityDescriptor {
  boolean isHasStartTag() {
    return hasStartTag;
  }

  void setHasStartTag(boolean hasStartTag) {
    this.hasStartTag = hasStartTag;
  }

  boolean isHasEndTag() {
    return hasEndTag;
  }

  void setHasEndTag(boolean hasEndTag) {
    this.hasEndTag = hasEndTag;
  }

  boolean isEmpty() {
    return empty;
  }

  void setEmpty(boolean empty) {
    this.empty = empty;
  }

  private boolean hasStartTag;
  private boolean hasEndTag;
  private boolean empty;
}
