// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.idea.svn.SvnBundle.message;

public enum NestedCopyType {
  external,
  switched,
  inner;

  public @NlsContexts.Label @NotNull String getDisplayName() {
    return message(switch (this) {
      case external -> "label.nested.copy.type.external";
      case switched -> "label.nested.copy.type.switched";
      case inner -> "label.nested.copy.type.inner";
    });
  }
}
