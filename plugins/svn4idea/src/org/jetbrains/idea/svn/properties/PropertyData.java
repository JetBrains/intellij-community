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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertyData {

  @Nullable private final PropertyValue myValue;
  @NotNull private final String myName;

  // TODO: Actually value is always not null for command line integration. But it is not clear enough if not null property value is always
  // TODO: provided by SVNKit. So currently value is @Nullable.
  public PropertyData(@NotNull String name, @Nullable PropertyValue value) {
    myName = name;
    myValue = value;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public PropertyValue getValue() {
    return myValue;
  }
}
