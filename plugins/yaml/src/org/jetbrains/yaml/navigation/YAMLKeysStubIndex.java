// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;


import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

/**
 * Now this index stores only config keys. It does not store keys under sequences.
 */
public class YAMLKeysStubIndex extends StringStubIndexExtension<YAMLKeyValue> {
  public static final StubIndexKey<String, YAMLKeyValue> KEY = StubIndexKey.createIndexKey("yaml.keys.full.path");

  public static final YAMLKeysStubIndex ourInstance = new YAMLKeysStubIndex();


  @NotNull
  @Override
  public StubIndexKey<String, YAMLKeyValue> getKey() {
    return KEY;
  }
}
