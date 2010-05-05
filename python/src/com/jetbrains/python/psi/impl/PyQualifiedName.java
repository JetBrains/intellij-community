package com.jetbrains.python.psi.impl;

import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author yole
 */
public class PyQualifiedName {
  private final List<String> myComponents;

  private PyQualifiedName(int count) {
    myComponents = new ArrayList<String>(count);
  }

  public PyQualifiedName(List<PyReferenceExpression> components) {
    myComponents = new ArrayList<String>(components.size());
    for (PyReferenceExpression component : components) {
      myComponents.add(component.getReferencedName());
    }
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

  public String toString() {
    StringBuilder sb = new StringBuilder();
    Iterator<String> it = myComponents.iterator();
    if (it.hasNext()) sb.append(it.next());
    while(it.hasNext()) sb.append(".").append(it.next());
    return sb.toString();
  }
}
