package com.intellij.psi.scope;

import com.intellij.psi.ResolveState;

public interface NameHint {
  String getName(ResolveState state);
}
