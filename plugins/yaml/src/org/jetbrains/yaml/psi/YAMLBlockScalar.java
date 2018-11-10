// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi;

/** See <a href="http://www.yaml.org/spec/1.2/spec.html#id2793652">8.1. Block Scalar Styles</a> */
public interface YAMLBlockScalar extends YAMLScalar {
  boolean hasExplicitIndent();
}
