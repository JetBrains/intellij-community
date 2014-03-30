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
package com.intellij.xml;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public interface XmlElementsGroup {

  enum Type {
    SEQUENCE,
    CHOICE,
    ALL,
    GROUP,

    LEAF
  }

  /**
   * @return minimal occurrence constraint value (e.g. 0 or 1)
   */
  int getMinOccurs();

  /**
   * @return maximal occurrence constraint value (e.g. 1 or {@link Integer#MAX_VALUE})
   */
  int getMaxOccurs();

  Type getGroupType();

  @Nullable
  XmlElementsGroup getParentGroup();

  List<XmlElementsGroup> getSubGroups();

  @Nullable
  XmlElementDescriptor getLeafDescriptor();
}
