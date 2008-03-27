package org.jetbrains.idea.svn.ignore;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Map;
import java.util.Set;

public interface IgnoreInfoGetter {
  Map<VirtualFile, Set<String>> getInfo(final boolean useCommonExtension);
}
