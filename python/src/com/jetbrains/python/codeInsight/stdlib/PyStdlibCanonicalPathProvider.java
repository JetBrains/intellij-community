package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.PyCanonicalPathProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyStdlibCanonicalPathProvider implements PyCanonicalPathProvider {
  @Nullable
  @Override
  public PyQualifiedName getCanonicalPath(PyQualifiedName qName, PsiElement foothold) {
    return restoreStdlibCanonicalPath(qName);
  }

  public static PyQualifiedName restoreStdlibCanonicalPath(PyQualifiedName qName) {
    if (qName.getComponentCount() > 0) {
      final List<String> components = qName.getComponents();
      final String head = components.get(0);
      if (head.equals("_abcoll") || head.equals("_collections")) {
        components.set(0, "collections");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("posix") || head.equals("nt")) {
        components.set(0, "os");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("_functools")) {
        components.set(0, "functools");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("_struct")) {
        components.set(0, "struct");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("_io") || head.equals("_pyio") || head.equals("_fileio")) {
        components.set(0, "io");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("_datetime")) {
        components.set(0, "datetime");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("ntpath") || head.equals("posixpath") || head.equals("path")) {
        final List<String> result = new ArrayList<String>();
        result.add("os");
        components.set(0, "path");
        result.addAll(components);
        return PyQualifiedName.fromComponents(result);
      }
    }
    return null;
  }
}
