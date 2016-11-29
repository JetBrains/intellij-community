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
      if (head.equals("_abcoll") || head.equals("_collections") || head.equals("_collections_abc")) {
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
        final List<String> result = new ArrayList<>();
        result.add("os");
        components.set(0, "path");
        result.addAll(components);
        return QualifiedName.fromComponents(result);
      }
      else if (head.equals("_sqlite3")) {
        components.set(0, "sqlite3");
        return QualifiedName.fromComponents(components);
      }
      else if (head.equals("_pickle")) {
        components.set(0, "pickle");
        return QualifiedName.fromComponents(components);
      }
    }
    return null;
  }
}
