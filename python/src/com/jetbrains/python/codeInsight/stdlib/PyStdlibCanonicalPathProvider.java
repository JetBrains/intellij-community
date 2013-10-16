package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyCanonicalPathProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyStdlibCanonicalPathProvider implements PyCanonicalPathProvider {
  @Nullable
  @Override
  public QualifiedName getCanonicalPath(@NotNull QualifiedName qName, PsiElement foothold) {
    return restoreStdlibCanonicalPath(qName);
  }

  public static QualifiedName restoreStdlibCanonicalPath(QualifiedName qName) {
    if (qName.getComponentCount() > 0) {
      final List<String> components = qName.getComponents();
      final String head = components.get(0);
      if (head.equals("_abcoll") || head.equals("_collections")) {
        components.set(0, "collections");
        return QualifiedName.fromComponents(components);
      }
      else if (head.equals("posix") || head.equals("nt")) {
        components.set(0, "os");
        return QualifiedName.fromComponents(components);
      }
      else if (head.equals("_functools")) {
        components.set(0, "functools");
        return QualifiedName.fromComponents(components);
      }
      else if (head.equals("_struct")) {
        components.set(0, "struct");
        return QualifiedName.fromComponents(components);
      }
      else if (head.equals("_io") || head.equals("_pyio") || head.equals("_fileio")) {
        components.set(0, "io");
        return QualifiedName.fromComponents(components);
      }
      else if (head.equals("_datetime")) {
        components.set(0, "datetime");
        return QualifiedName.fromComponents(components);
      }
      else if (head.equals("ntpath") || head.equals("posixpath") || head.equals("path")) {
        final List<String> result = new ArrayList<String>();
        result.add("os");
        components.set(0, "path");
        result.addAll(components);
        return QualifiedName.fromComponents(result);
      }
    }
    return null;
  }
}
