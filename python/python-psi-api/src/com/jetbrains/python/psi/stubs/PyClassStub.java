// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface PyClassStub extends NamedStub<PyClass>, PyVersionSpecificStub {

  /**
   * @return a {@code Map} which contains imported class names as keys and their original names as values.
   * <i>Note: the returned {@code Map} could contain nulls as keys and as values.</i>
   */
  @NotNull
  Map<QualifiedName, QualifiedName> getSuperClasses();

  @Nullable
  QualifiedName getMetaClass();

  @Nullable
  List<String> getSlots();

  @Nullable
  List<String> getMatchArgs();

  @Nullable
  String getDocString();

  @Nullable
  String getDeprecationMessage();

  /**
   * @return literal text of expressions in the base classes list.
   */
  @NotNull
  List<String> getSuperClassesText();


  @ApiStatus.Internal
  default @Nullable <T> T getCustomStub(@NotNull Class<T> stubClass) {
    return null;
  }
}