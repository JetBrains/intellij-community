// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

public class YAMLElementType extends IElementType {
  public YAMLElementType(@NonNls String debugName) {
    super(debugName, YAMLFileType.YML.getLanguage());
  }
}
