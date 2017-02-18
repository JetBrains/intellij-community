/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author vlan
 */
public class PySetuptoolsNamespaceIndex extends ScalarIndexExtension<String> {
  public static final ID<String, Void> NAME = ID.create("Py.setuptools.namespace");
  private static final Pattern RE_NAMESPACE = Pattern.compile("sys\\.modules\\.setdefault\\('([^']*)'");
  private static final String NAMESPACE_FILE_SUFFIX = "-nspkg.pth";

  private final DataIndexer<String, Void, FileContent> myDataIndexer = new DataIndexer<String, Void, FileContent>() {
    @NotNull
    @Override
    public Map<String, Void> map(@NotNull FileContent inputData) {
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

  private FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    @Override
    public boolean acceptInput(@NotNull VirtualFile file) {
      return StringUtil.endsWith(file.getNameSequence(), NAMESPACE_FILE_SUFFIX);
    }
  };

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
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

  @NotNull
  public static Collection<VirtualFile> find(@NotNull String name, @NotNull Project project) {
    final GlobalSearchScope scope = PyProjectScopeBuilder.excludeSdkTestsScope(project);
    return FileBasedIndex.getInstance().getContainingFiles(NAME, name, scope);
  }
}
