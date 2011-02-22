package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyQualifiedName {
  private final List<String> myComponents;

  private PyQualifiedName(int count) {
    myComponents = new ArrayList<String>(count);
  }

  @Nullable
  public static PyQualifiedName fromReferenceChain(List<PyReferenceExpression> components) {
    PyQualifiedName qName = new PyQualifiedName(components.size());
    for (PyReferenceExpression component : components) {
      final String refName = component.getReferencedName();
      if (refName == null) {
        return null;
      }
      qName.myComponents.add(refName);
    }
    return qName;
  }

  public static PyQualifiedName fromComponents(Collection<String> components) {
    PyQualifiedName qName = new PyQualifiedName(components.size());
    qName.myComponents.addAll(components);
    return qName;
  }

  public static PyQualifiedName fromComponents(String... components) {
    PyQualifiedName result = new PyQualifiedName(components.length);
    Collections.addAll(result.myComponents, components);
    return result;
  }

  public PyQualifiedName append(String name) {
    PyQualifiedName result = new PyQualifiedName(myComponents.size()+1);
    result.myComponents.addAll(myComponents);
    result.myComponents.add(name);
    return result;
  }

  @NotNull
  public PyQualifiedName removeLastComponent() {
    return removeTail(1);
  }

  @NotNull
  public PyQualifiedName removeTail(int count) {
    int size = myComponents.size();
    PyQualifiedName result = new PyQualifiedName(size);
    result.myComponents.addAll(myComponents);
    for (int i = 0; i < count && result.myComponents.size() > 0; i++) {
      result.myComponents.remove(result.myComponents.size()-1);
    }
    return result;
  }

  public List<String> getComponents() {
    return myComponents;
  }

  public int getComponentCount() {
    return myComponents.size();
  }

  public boolean matches(String... components) {
    if (myComponents.size() != components.length) {
      return false;
    }
    for (int i = 0; i < myComponents.size(); i++) {
      if (!myComponents.get(i).equals(components[i])) {
        return false;
      }
    }
    return true;
  }

  public boolean matchesPrefix(PyQualifiedName prefix) {
    if (getComponentCount() < prefix.getComponentCount()) {
      return false;
    }
    for (int i = 0; i < prefix.getComponentCount(); i++) {
      final String component = getComponents().get(i);
      if (component == null || !component.equals(prefix.getComponents().get(i))) {
        return false;
      }
    }
    return true;
  }

  public boolean endsWith(@NotNull String suffix) {
    return suffix.equals(getLastComponent());
  }

  public static void serialize(@Nullable PyQualifiedName qName, StubOutputStream dataStream) throws IOException {
    if (qName == null) {
      dataStream.writeVarInt(0);
    }
    else {
      dataStream.writeVarInt(qName.getComponentCount());
      for (String s : qName.myComponents) {
        dataStream.writeName(s);
      }
    }
  }

  @Nullable
  public static PyQualifiedName deserialize(StubInputStream dataStream) throws IOException {
    PyQualifiedName qName;
    int size = dataStream.readVarInt();
    if (size == 0) {
      qName = null;
    }
    else {
      qName = new PyQualifiedName(size);
      for (int i = 0; i < size; i++) {
        final StringRef name = dataStream.readName();
        qName.myComponents.add(name == null ? null : name.getString());
      }
    }
    return qName;
  }

  @Nullable
  public String getLastComponent() {
    if (myComponents.size() == 0) {
      return null;
    }
    return myComponents.get(myComponents.size()-1);
  }

  @Override
  public String toString() {
    return join(".");
  }

  public String join(final String separator) {
    return StringUtil.join(myComponents, separator);
  }

  public static PyQualifiedName fromDottedString(String refName) {
    return fromComponents(refName.split("\\."));
  }

  @Nullable
  public static PyQualifiedName fromExpression(PyExpression expr) {
    return expr instanceof PyReferenceExpression ? ((PyReferenceExpression) expr).asQualifiedName() : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyQualifiedName that = (PyQualifiedName)o;

    if (myComponents != null ? !myComponents.equals(that.myComponents) : that.myComponents != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myComponents != null ? myComponents.hashCode() : 0;
  }
}
