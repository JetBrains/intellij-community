// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.psi.tree.IStubFileElementType;

public class YAMLFileElementType extends IStubFileElementType {
  public YAMLFileElementType() {
    super(YAMLLanguage.INSTANCE);
  }

  @Override
  public int getStubVersion() {
    return 1;
  }
}
