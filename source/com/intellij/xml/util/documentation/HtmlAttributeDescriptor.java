package com.intellij.xml.util.documentation;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2004
 * Time: 23:54:11
 * To change this template use File | Settings | File Templates.
 */
class HtmlAttributeDescriptor extends EntityDescriptor {
  private String myType;
  private boolean myHasDefaultValue;
  private String[] mySetOfParentTags;
  private boolean myParentSetIsExclusionSet;

  boolean isValidParentTagName(String str) {
    boolean containsInSet = Arrays.binarySearch(mySetOfParentTags, str) >= 0;
    return containsInSet == !myParentSetIsExclusionSet;
  }

  String getType() {
    return myType;
  }

  void setType(String type) {
    this.myType = type;
  }

  boolean isHasDefaultValue() {
    return myHasDefaultValue;
  }

  void setHasDefaultValue(boolean hasDefaultValue) {
    this.myHasDefaultValue = hasDefaultValue;
  }

  String[] getSetOfParentTags() {
    return mySetOfParentTags;
  }

  boolean isParentSetIsExclusionSet() {
    return myParentSetIsExclusionSet;
  }

  void setParentSetIsExclusionSet(boolean _parentSetIsExclusionSet) {
    myParentSetIsExclusionSet = _parentSetIsExclusionSet;
  }

  void setSetOfParentTags(String[] _setOfParentTags) {
    mySetOfParentTags = _setOfParentTags;
  }
}
