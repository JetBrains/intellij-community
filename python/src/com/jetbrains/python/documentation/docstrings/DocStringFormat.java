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
package com.jetbrains.python.documentation.docstrings;

import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public enum DocStringFormat {
  /**
   * @see DocStringUtil#ensureNotPlainDocstringFormat(PsiElement)
   */
  PLAIN("Plain"),
  EPYTEXT("Epytext"),
  REST("reStructuredText"),
  NUMPY("NumPy"),
  GOOGLE("Google");

  public static final List<String> ALL_NAMES = getAllNames();

  @NotNull
  private static List<String> getAllNames() {
    return Collections.unmodifiableList(ContainerUtil.map(values(), new Function<DocStringFormat, String>() {
      @Override
      public String fun(DocStringFormat format) {
        return format.getName();
      }
    }));
  }

  public static final List<String> ALL_NAMES_BUT_PLAIN = getAllNamesButPlain();

  @NotNull
  private static List<String> getAllNamesButPlain() {
    return Collections.unmodifiableList(ContainerUtil.mapNotNull(values(), new Function<DocStringFormat, String>() {
      @Override
      public String fun(DocStringFormat format) {
        return format == PLAIN ? null : format.getName();
      }
    }));
  }

  @Nullable
  public static DocStringFormat fromName(@NotNull String name) {
    for (DocStringFormat format : values()) {
      if (format.getName().equalsIgnoreCase(name)) {
        return format;
      }
    }
    return null;
  }

  @NotNull
  public static DocStringFormat fromNameOrPlain(@NotNull String name) {
    return ObjectUtils.notNull(fromName(name), PLAIN);
  }

  String myName;

  DocStringFormat(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

}
