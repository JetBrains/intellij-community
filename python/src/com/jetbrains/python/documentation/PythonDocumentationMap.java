package com.jetbrains.python.documentation;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
@State(
  name = "PythonDocumentationMap",
  storages = {
  @Storage(
    id = "other",
    file = "$APP_CONFIG$/other.xml")
    }
)
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
    private List<Entry> myEntries = new ArrayList<Entry>();

    public List<Entry> getEntries() {
      return myEntries;
    }

    public void setEntries(List<Entry> entries) {
      myEntries = entries;
    }
  }

  private State myState = new State();

  public PythonDocumentationMap() {
    addEntry("compiler", "http://docs.python.org/{python.version}/library/compiler.html#{element.qname}");
    addEntry("PyQt4", "http://www.riverbankcomputing.co.uk/static/Docs/PyQt4/html/{class.name.lower}.html#{function.name}");
    addEntry("PySide", "http://www.pyside.org/docs/pyside/{module.name.slashes}/{class.name}.html#{module.name}.{element.qname}");
    addEntry("gtk", "http://library.gnome.org/devel/pygtk/stable/class-gtk{class.name.lower}.html#method-gtk{class.name.lower}--{function.name.dashes}");
  }

  private void addEntry(String qName, String pattern) {
    myState.myEntries.add(new Entry(qName, pattern));
  }

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
  public String urlFor(PyQualifiedName moduleQName, @Nullable PsiNamedElement element, String pyVersion) {
    for (Entry entry : myState.myEntries) {
      if (moduleQName.matchesPrefix(PyQualifiedName.fromDottedString(entry.myPrefix))) {
        return transformPattern(entry.myUrlPattern, moduleQName, element, pyVersion);
      }
    }
    return null;
  }

  @Nullable
  private static String transformPattern(String urlPattern, PyQualifiedName moduleQName, @Nullable PsiNamedElement element, String pyVersion) {
    Map<String, String> macros = new HashMap<String, String>();
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
    return transformPattern(urlPattern, macros);
  }

  @Nullable
  private static String transformPattern(String urlPattern, Map<String, String> macroValues) {
    for (Map.Entry<String, String> entry : macroValues.entrySet()) {
      if (entry.getValue() == null && urlPattern.contains("{" + entry.getKey())) {
        return null;
      }
      urlPattern = urlPattern
        .replace("{" + entry.getKey() + "}", entry.getValue())
        .replace("{" + entry.getKey() + ".lower}", entry.getValue().toLowerCase())
        .replace("{" + entry.getKey() + ".slashes}", entry.getValue().replace(".", "/"))
        .replace("{" + entry.getKey() + ".dashes}", entry.getValue().replace("_", "-"));

    }
    return urlPattern;
  }
}
