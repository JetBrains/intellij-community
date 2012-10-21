package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
* Simple visitor to use with ResolveImportUtil.
* User: dcheryasov
* Date: Apr 6, 2010 8:06:46 PM
*/
public interface RootVisitor {
  /**
   * @param root what we're visiting.
   * @param module the module to which the root belongs, or null
   * @param sdk the SDK to which the root belongs, or null
   *
   * @return false when visiting must stop.
   */
  boolean visitRoot(VirtualFile root, @Nullable Module module, @Nullable Sdk sdk, boolean isModuleSource);
}
