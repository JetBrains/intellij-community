// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Simple visitor to use with ResolveImportUtil.
 * User: dcheryasov
 */
@ApiStatus.Internal
public interface RootVisitor {
  /**
   * @param root   what we're visiting.
   * @param module the module to which the root belongs, or null
   * @param isModuleSource true iff the root is a source root or the content root of a module
   *
   * @return false when visiting must stop.
   */
  boolean visitRoot(VirtualFile root, @Nullable Module module, boolean isModuleSource);
}
