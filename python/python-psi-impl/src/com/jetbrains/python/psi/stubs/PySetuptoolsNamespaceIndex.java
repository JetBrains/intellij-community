// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.indexing.hints.BinaryFileTypePolicy;
import com.intellij.util.indexing.hints.FileNameSuffixInputFilter;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class PySetuptoolsNamespaceIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> NAME = ID.create("Py.setuptools.namespace");
  private static final Pattern RE_NAMESPACE = Pattern.compile("sys\\.modules\\.setdefault\\('([^']*)'");
  private static final String NAMESPACE_FILE_SUFFIX = "-nspkg.pth";

  private final DataIndexer<String, Void, FileContent> myDataIndexer = new DataIndexer<>() {
    @Override
    public @NotNull Map<String, Void> map(@NotNull FileContent inputData) {
      final CharSequence content = inputData.getContentAsText();
      final Matcher matcher = RE_NAMESPACE.matcher(content);
      final Map<String, Void> results = new HashMap<>();
      while (matcher.find()) {
        final String packageName = matcher.group(1);
        results.put(packageName, null);
      }
      return results;
    }
  };

  private final FileBasedIndex.InputFilter myInputFilter =
    new FileNameSuffixInputFilter(NAMESPACE_FILE_SUFFIX, false /* don't ignore case */, BinaryFileTypePolicy.NON_BINARY);

  @Override
  public @NotNull ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  public static @NotNull Collection<VirtualFile> find(@NotNull String name, @NotNull Project project) {
    final GlobalSearchScope scope = PySearchUtilBase.excludeSdkTestsScope(project);
    return FileBasedIndex.getInstance().getContainingFiles(NAME, name, scope);
  }
}
