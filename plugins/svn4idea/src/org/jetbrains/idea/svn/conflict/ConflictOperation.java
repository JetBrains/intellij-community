// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.conflict;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlEnumValue;

public enum ConflictOperation {
  @XmlEnumValue("") NONE,
  @XmlEnumValue("update") UPDATE,
  @XmlEnumValue("switch") SWITCH,
  @XmlEnumValue("merge") MERGE;

  public static @NotNull ConflictOperation from(@NotNull @NonNls String operationName) {
    return valueOf(ConflictOperation.class, StringUtil.toUpperCase(operationName));
  }

  @Override
  public @NonNls String toString() {
    return StringUtil.toLowerCase(super.toString());
  }
}
