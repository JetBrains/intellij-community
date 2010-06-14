package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.vfs.VirtualFile;

/**
* Simple visitor to use with ResolveImportUtil.
* User: dcheryasov
* Date: Apr 6, 2010 8:06:46 PM
*/
public interface RootVisitor {
  /**
   * @param root what we're visiting.
   * @return false when visiting must stop.
   */
  boolean visitRoot(VirtualFile root);
}
