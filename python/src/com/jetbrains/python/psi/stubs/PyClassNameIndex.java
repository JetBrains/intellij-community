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
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PyClassNameIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String,PyClass> KEY = StubIndexKey.createIndexKey("Py.class.shortName");

  @NotNull
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PyClass.class);
  }

  public static Collection<PyClass> find(String name, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? PyProjectScopeBuilder.excludeSdkTestsScope(project)
                                    : GlobalSearchScope.projectScope(project);
    return find(name, project, scope);
  }


  /**
   * @deprecated use {@link com.jetbrains.python.psi.PyPsiFacade#createClassByQName(String, PsiElement)} or skeleton may be found
   */
  @Deprecated
  @Nullable
  public static PyClass findClass(@NotNull String qName, Project project, GlobalSearchScope scope) {
    int pos = qName.lastIndexOf(".");
    String shortName = pos > 0 ? qName.substring(pos+1) : qName;
    for (PyClass pyClass : find(shortName, project, scope)) {
      if (qName.equals(pyClass.getQualifiedName())) {
        return pyClass;
      }
    }
    return null;
  }

  /**
   * @deprecated use {@link com.jetbrains.python.psi.PyPsiFacade#createClassByQName(String, PsiElement)} or skeleton may be found
   */
  @Deprecated
  @Nullable
  public static PyClass findClass(@Nullable String qName, Project project) {
    if (qName == null) {
      return null;
    }
    return findClass(qName, project, ProjectScope.getAllScope(project));
  }

  public static Collection<String> allKeys(Project project) {
    return StubIndex.getInstance().getAllKeys(KEY, project);
  }
}