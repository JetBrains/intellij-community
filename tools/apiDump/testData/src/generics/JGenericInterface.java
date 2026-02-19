// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump.testData.generics;

import java.util.List;

public interface JGenericInterface<T> {

  T returningT();

  T[] returningAT();

  Iterable<T> returningLT();

  void acceptingT(T val);

  void acceptingAT(T[] val);

  void acceptingLT(List<T> val);
}
