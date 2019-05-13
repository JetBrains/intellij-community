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

/**
 * @author maxim
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
