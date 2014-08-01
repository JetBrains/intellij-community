/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.properties;

import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.wc.SVNPropertyData;

/**
 * @author Konstantin Kolosovsky.
 */
public class PropertyData {

  private final PropertyValue myValue;

  private final String myName;

  public PropertyData(String name, PropertyValue value) {
    myName = name;
    myValue = value;
  }

  public String getName() {
    return myName;
  }

  public PropertyValue getValue() {
    return myValue;
  }

  @Nullable
  public static PropertyData create(@Nullable SVNPropertyData data) {
    PropertyData result = null;

    if (data != null) {
      result = new PropertyData(data.getName(), PropertyValue.create(data.getValue()));
    }

    return result;
  }
}
