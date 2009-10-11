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
