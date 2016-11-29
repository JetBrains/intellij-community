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
package com.jetbrains.python.documentation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
@State(name = "PythonDocumentationMap", storages = @Storage("other.xml"))
public class PythonDocumentationMap implements PersistentStateComponent<PythonDocumentationMap.State> {
  public static PythonDocumentationMap getInstance() {
    return ServiceManager.getService(PythonDocumentationMap.class);
  }

  public static class Entry {
    private String myPrefix;
    private String myUrlPattern;

    public Entry() {
    }

    public Entry(String prefix, String urlPattern) {
      myPrefix = prefix;
      myUrlPattern = urlPattern;
    }

    public String getPrefix() {
      return myPrefix;
    }

    public String getUrlPattern() {
      return myUrlPattern;
    }

    public void setPrefix(String prefix) {
      myPrefix = prefix;
    }

    public void setUrlPattern(String urlPattern) {
      myUrlPattern = urlPattern;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Entry entry = (Entry)o;

      if (!myPrefix.equals(entry.myPrefix)) return false;
      if (!myUrlPattern.equals(entry.myUrlPattern)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myPrefix.hashCode();
      result = 31 * result + myUrlPattern.hashCode();
      return result;
    }
  }

  public static class State {
    private List<Entry> myEntries = new ArrayList<>();

    public State() {
      addEntry("PyQt4", "http://www.riverbankcomputing.co.uk/static/Docs/PyQt4/html/{class.name.lower}.html#{function.name}");
      addEntry("PySide", "http://pyside.github.io/docs/pyside/{module.name.slashes}/{class.name}.html#{module.name}.{element.qname}");
      addEntry("gtk", "http://library.gnome.org/devel/pygtk/stable/class-gtk{class.name.lower}.html#method-gtk{class.name.lower}--{function.name.dashes}");
      addEntry("wx", "http://www.wxpython.org/docs/api/{module.name}.{class.name}-class.html#{function.name}");
      addEntry("numpy", "http://docs.scipy.org/doc/numpy/reference/{}generated/{module.name}.{element.name}.html");
      addEntry("scipy", "http://docs.scipy.org/doc/scipy/reference/{}generated/{module.name}.{element.name}.html");
      addEntry("kivy", "http://kivy.org/docs/api-{module.name}.html");
    }

    public List<Entry> getEntries() {
      return myEntries;
    }

    public void setEntries(List<Entry> entries) {
      myEntries = entries;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      State state = (State)o;
      return Sets.newHashSet(myEntries).equals(Sets.newHashSet(state.getEntries()));
    }

    @Override
    public int hashCode() {
      return myEntries != null ? myEntries.hashCode() : 0;
    }

    private void addEntry(String qName, String pattern) {
      myEntries.add(new Entry(qName, pattern));
    }
  }

  private State myState = new State();

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public List<Entry> getEntries() {
    return ImmutableList.copyOf(myState.getEntries());
  }

  public void setEntries(List<Entry> entries) {
    myState.setEntries(entries);
  }

  @Nullable
  public String urlFor(QualifiedName moduleQName, @Nullable PsiNamedElement element, String pyVersion) {
    for (Entry entry : myState.myEntries) {
      if (moduleQName.matchesPrefix(QualifiedName.fromDottedString(entry.myPrefix))) {
        return transformPattern(entry.myUrlPattern, moduleQName, element, pyVersion);
      }
    }
    return null;
  }

  @Nullable
  public String rootUrlFor(QualifiedName moduleQName) {
    for (Entry entry : myState.myEntries) {
      if (moduleQName.matchesPrefix(QualifiedName.fromDottedString(entry.myPrefix))) {
        return rootForPattern(entry.myUrlPattern);
      }
    }
    return null;
  }

  private static String rootForPattern(String urlPattern) {
    int pos = urlPattern.indexOf('{');
    return pos >= 0 ? urlPattern.substring(0, pos) : urlPattern;
  }

  @Nullable
  private static String transformPattern(@NotNull String urlPattern, QualifiedName moduleQName, @Nullable PsiNamedElement element,
                                         String pyVersion) {
    Map<String, String> macros = new HashMap<>();
    macros.put("element.name", element == null ? null : element.getName());
    PyClass pyClass = element == null ? null : PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    macros.put("class.name", pyClass == null ? null : pyClass.getName());
    if (element != null) {
      StringBuilder qName = new StringBuilder(moduleQName.toString()).append(".");
      if (element instanceof PyFunction && ((PyFunction)element).getContainingClass() != null) {
        qName.append(((PyFunction)element).getContainingClass().getName()).append(".");
      }
      qName.append(element.getName());
      macros.put("element.qname", qName.toString());
    }
    else {
      macros.put("element.qname", "");
    }
    macros.put("function.name", element instanceof PyFunction ? element.getName() : "");
    macros.put("module.name", moduleQName.toString());
    macros.put("python.version", pyVersion);
    final String pattern = transformPattern(urlPattern, macros);
    if (pattern == null) {
      return rootForPattern(urlPattern);
    }
    return pattern;
  }

  @Nullable
  private static String transformPattern(@NotNull String urlPattern, Map<String, String> macroValues) {
    for (Map.Entry<String, String> entry : macroValues.entrySet()) {
      if (entry.getValue() == null) {
        if (urlPattern.contains("{" + entry.getKey())) {
          return null;
        }
        continue;
      }
      urlPattern = urlPattern
        .replace("{" + entry.getKey() + "}", entry.getValue())
        .replace("{" + entry.getKey() + ".lower}", entry.getValue().toLowerCase())
        .replace("{" + entry.getKey() + ".slashes}", entry.getValue().replace(".", "/"))
        .replace("{" + entry.getKey() + ".dashes}", entry.getValue().replace("_", "-"));

    }
    return urlPattern.replace("{}", "");
  }
}
