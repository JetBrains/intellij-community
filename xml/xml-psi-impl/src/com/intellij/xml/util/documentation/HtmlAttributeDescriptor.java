/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.util.documentation;

import java.util.Arrays;

/**
 * @author maxim
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
