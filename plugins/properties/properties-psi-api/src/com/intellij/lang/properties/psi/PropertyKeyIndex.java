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

/*
 * @author max
 */
package com.intellij.lang.properties.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PropertyKeyIndex extends StringStubIndexExtension<Property> {
  public static final StubIndexKey<String, Property> KEY = StubIndexKey.createIndexKey("properties.index");

  private static final PropertyKeyIndex ourInstance = new PropertyKeyIndex();

  public static PropertyKeyIndex getInstance() {
    return ourInstance;
  }

  @NotNull
  public StubIndexKey<String, Property> getKey() {
    return KEY;
  }

  @Override
  public Collection<Property> get(@NotNull String s, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), s, project, scope, Property.class);
  }
}
