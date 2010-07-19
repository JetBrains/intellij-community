/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * XSD/DTD Model generator tool
 *
 * By Gregory Shrago
 * 2002 - 2006
 */
package com.intellij.util.dom.generator;

/**
 * @author Konstantin Bulenkov
 */
public class FieldDesc implements Comparable<FieldDesc> {
  final static int STR = 1;
  final static int BOOL = 2;
  final static int OBJ = 3;
  final static int ATTR = 4;
  final static int DOUBLE = 5;
  final static int SIMPLE = 6;

  public FieldDesc(String name, String def) {
    this.name = name;
    this.def = def;
  }

  public FieldDesc(int clType, String name, String type, String elementType, String def, boolean required) {
    this.clType = clType;
    this.name = name;
    this.type = type;
    this.elementType = elementType;
    this.def = def;
    this.required = required;
  }

  int clType = STR;
  String name;
  String type;
  String elementType;
  String def;
  boolean required;

  int idx;
  String tagName;
  String elementName;
  String comment;
  FieldDesc[] choice;
  boolean choiceOpt;

  String documentation;
  String simpleTypesString;
  int duplicateIndex = -1;
  int realIndex;
  String contentQualifiedName;

  public int compareTo(FieldDesc o) {
    return name.compareTo(o.name);
  }

  public String toString() {
    return "Field: " + name + ";" + type + ";" + elementName + ";" + elementType;
  }

}
