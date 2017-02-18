/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.xml.stubs.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.CommonProcessors;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;

/**
 * @since 13.0
 */
public class DomNamespaceKeyIndex extends StringStubIndexExtension<PsiFile> {

  public static final StubIndexKey<String, PsiFile> KEY = StubIndexKey.createIndexKey("dom.namespaceKey");

  private static final DomNamespaceKeyIndex ourInstance = new DomNamespaceKeyIndex();

  public static DomNamespaceKeyIndex getInstance() {
    return ourInstance;
  }

  public boolean hasStubElementsWithNamespaceKey(final DomFileElement domFileElement, final String namespaceKey) {
    final VirtualFile file = domFileElement.getFile().getVirtualFile();
    if (!(file instanceof VirtualFileWithId)) return false;

    final int virtualFileId = ((VirtualFileWithId)file).getId();
    CommonProcessors.FindFirstProcessor<PsiFile> processor = new CommonProcessors.FindFirstProcessor<>();
    StubIndex.getInstance().processElements(
      KEY,
      namespaceKey,
      domFileElement.getFile().getProject(),
      GlobalSearchScope.fileScope(domFileElement.getFile()),
      new IdFilter() {
        @Override
        public boolean containsFileId(int id) {
          return id == virtualFileId;
        }
      },
      PsiFile.class, 
      processor
    );
    return processor.isFound();
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiFile> getKey() {
    return KEY;
  }

  @Override
  public int getVersion() {
    return 1;
  }
}
