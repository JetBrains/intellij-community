// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public interface YAMLKeyStub extends StubElement<YAMLKeyValue> {
  @NotNull
  String getKeyPath();

  @NotNull
  String getKeyText();
}
