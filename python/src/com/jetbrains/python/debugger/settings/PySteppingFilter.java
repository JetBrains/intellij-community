/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

public class PySteppingFilter {
  private boolean myIsEnabled;
  private @NotNull String myFilter;

  public PySteppingFilter() {
    myIsEnabled = true;
    myFilter = "";
  }

  public PySteppingFilter(boolean isEnabled, @NotNull String filter) {
    myIsEnabled = isEnabled;
    myFilter = filter;
  }

  public boolean isEnabled() {
    return myIsEnabled;
  }

  public void setEnabled(boolean enabled) {
    myIsEnabled = enabled;
  }

  @NotNull
  public String getFilter() {
    return myFilter;
  }

  @NotNull
  public String getAbsolutePlatformIndependentFilter(@NotNull Project project) {
    StringBuilder resultFilter = new StringBuilder();
    final String[] filters = myFilter.split(PyDebuggerSettings.FILTERS_DIVIDER);
    for (String filter : filters) {
      if (!(FileUtil.isAbsolutePlatformIndependent(filter) || filter.startsWith("*"))) {
        resultFilter.append(project.getBasePath()).append('/');
      }
      resultFilter.append(filter).append(PyDebuggerSettings.FILTERS_DIVIDER);
    }
    return resultFilter.toString().replace('\\', '/');
  }

  public void setFilter(@NotNull String filter) {
    myFilter = filter;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PySteppingFilter)) return false;

    PySteppingFilter filter = (PySteppingFilter)o;

    if (isEnabled() != filter.isEnabled()) return false;
    if (!getFilter().equals(filter.getFilter())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (isEnabled() ? 1 : 0);
    result = 31 * result + getFilter().hashCode();
    return result;
  }
}
