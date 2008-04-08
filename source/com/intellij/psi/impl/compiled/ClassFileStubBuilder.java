/*
 * @author max
 */
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.cls.ClsFormatException;

public class ClassFileStubBuilder implements BinaryFileStubBuilder {
  public boolean acceptsFile(final VirtualFile file) {
    return !isAnonymousOrChildOf(file.getNameWithoutExtension());
  }

  private static boolean isAnonymousOrChildOf(final String name) {
    int len = name.length();
    int idx = name.indexOf('$');
    while (idx > 0) {
      if (idx >= len || Character.isDigit(name.charAt(idx + 1))) return true;
      idx = name.indexOf('$', idx + 1);
    }
    return false;
  }

  public StubElement buildStubTree(final byte[] content) {
    try {
      return ClsStubBuilder.build(content);
    }
    catch (ClsFormatException e) {
      return null;
    }
  }
}