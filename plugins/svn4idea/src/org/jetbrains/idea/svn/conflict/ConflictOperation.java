// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public static ConflictOperation from(@NotNull @NonNls String operationName) {
    return valueOf(ConflictOperation.class, StringUtil.toUpperCase(operationName));
  }

  @Override
  @NonNls
  public String toString() {
    return StringUtil.toLowerCase(super.toString());
  }
}
