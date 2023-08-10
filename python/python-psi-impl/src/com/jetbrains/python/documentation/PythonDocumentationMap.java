// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@State(name = "PythonDocumentationMap", storages = @Storage("other.xml"))
public class PythonDocumentationMap implements PersistentStateComponent<PythonDocumentationMap.State> {
  private static final ImmutableMap<String, String> DEFAULT_ENTRIES = ImmutableMap.<String, String>builder()
    .put("PyQt4", "http://pyqt.sourceforge.net/Docs/PyQt4/{class.name.lower}.html#{function.name}")
    .put("PyQt5", "http://doc.qt.io/qt-5/{class.name.lower}.html#{functionToProperty.name}{functionIsProperty?-prop}")
    .put("PySide", "http://pyside.github.io/docs/pyside/{module.name.slashes}/{class.name}.html#{module.name}.{element.qname}")
    .put("gtk","http://library.gnome.org/devel/pygtk/stable/class-gtk{class.name.lower}.html#method-gtk{class.name.lower}--{function.name.dashes}")
    .put("wx", "http://www.wxpython.org/docs/api/{module.name}.{class.name}-class.html#{function.name}")
    .put("kivy", "http://kivy.org/docs/api-{module.name}.html")
    .put("matplotlib", "http://matplotlib.org/api/{module.basename}_api.html#{element.qname}")
    .put("pyramid", "http://docs.pylonsproject.org/projects/pyramid/en/latest/api/{module.basename}.html#{element.qname}")
    .put("flask", "http://flask.pocoo.org/docs/latest/api/#{element.qname}")
    .put("pandas", "https://pandas.pydata.org/pandas-docs/stable/generated/{element.qname}.html")
    .build();

  public static PythonDocumentationMap getInstance() {
    return ApplicationManager.getApplication().getService(PythonDocumentationMap.class);
  }

  public static class State {
    private Map<String, String> myEntries = Maps.newHashMap(DEFAULT_ENTRIES);

    public Map<String, String> getEntries() {
      return myEntries;
    }

    public void setEntries(Map<String, String> entries) {
      myEntries = entries;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      State state = (State)o;
      return myEntries.equals(state.getEntries());
    }

    @Override
    public int hashCode() {
      return myEntries != null ? myEntries.hashCode() : 0;
    }
  }

  private State myState = new State();

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;

    addAbsentEntriesFromDefaultState(myState);
  }

  private static void addAbsentEntriesFromDefaultState(@NotNull State state) {
    DEFAULT_ENTRIES.forEach((key, value) -> state.getEntries().putIfAbsent(key, value));
  }

  public Map<String, String> getEntries() {
    return Map.copyOf(myState.getEntries());
  }

  public void setEntries(Map<String, String> entries) {
    myState.setEntries(entries);
  }

  @Nullable
  public String urlFor(QualifiedName moduleQName, @Nullable PsiNamedElement element, String pyVersion) {
    for (Map.Entry<String, String> entry : myState.getEntries().entrySet()) {
      if (moduleQName.matchesPrefix(QualifiedName.fromDottedString(entry.getKey()))) {
        return transformPattern(entry.getValue(), moduleQName, element, pyVersion);
      }
    }
    return null;
  }

  public boolean isUserDefinedUrl(@NotNull String url) {
    return ContainerUtil.exists(getEntries().values(),
                                pattern -> url.startsWith(rootForPattern(pattern)) && !DEFAULT_ENTRIES.containsValue(pattern));
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
    String functionName = element instanceof PyFunction && element.getName() != null ? element.getName() : "";
    macros.put("function.name", functionName);
    macros.put("functionToProperty.name", functionToProperty(functionName));
    macros.put("module.name", moduleQName.toString());
    macros.put("python.version", pyVersion);
    macros.put("module.basename", moduleQName.getLastComponent());

    macros.put("functionIsProperty?", Boolean.toString(!functionName.equals(functionToProperty(functionName))));

    final String pattern = transformPattern(urlPattern, macros);
    if (pattern == null) {
      return rootForPattern(urlPattern);
    }
    return pattern;
  }

  private static String functionToProperty(@NotNull String name) {
    String functionOrProp = StringUtil.getPropertyName(name);
    if (functionOrProp == null) {
      functionOrProp = name;
    }
    return functionOrProp;
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
      if (entry.getKey().endsWith("?")) {
        String regex = "\\{" + Pattern.quote(entry.getKey()) + "([^}]+)\\}";

        Matcher matcher = Pattern.compile(regex).matcher(urlPattern);
        if (matcher.find()) {
          String value = Boolean.parseBoolean(entry.getValue()) ? matcher.group(1) : "";

          urlPattern = urlPattern.replaceAll(regex, value);
        }
      }
      urlPattern = urlPattern
        .replace("{" + entry.getKey() + "}", entry.getValue())
        .replace("{" + entry.getKey() + ".lower}", StringUtil.toLowerCase(entry.getValue()))
        .replace("{" + entry.getKey() + ".slashes}", entry.getValue().replace(".", "/"))
        .replace("{" + entry.getKey() + ".dashes}", entry.getValue().replace("_", "-"));
    }
    return urlPattern.replace("{}", "");
  }
}
