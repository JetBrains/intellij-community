/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

/**
 * Like {@link YamlDictionaryClass} but allows arbitrary level of unstructured nesting
 */
@ApiStatus.Experimental
public class YamlUnstructuredClass extends YamlMetaClass {
  private static YamlUnstructuredClass ourInstance;

  public static YamlMetaClass getInstance() {
    if (ourInstance == null) {
      ourInstance = new YamlUnstructuredClass();
      ourInstance.addFeature(new Field("anything:<any-key>", ourInstance))
        .withAnyName()
        .withEmptyValueAllowed(true);
    }
    return ourInstance;
  }

  public YamlUnstructuredClass() {
    super("yaml:anything");
  }

  @Override
  public void validateKeyValue(@NotNull YAMLKeyValue keyValue, @NotNull ProblemsHolder problemsHolder) {
    // allow everything
  }
}
