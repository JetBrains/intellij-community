// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.stubs.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.CommonProcessors;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;

/**
 * @see com.intellij.util.xml.StubbedOccurrence
 */
public class DomElementClassIndex extends StringStubIndexExtension<PsiFile> {

  public static final StubIndexKey<String, PsiFile> KEY = StubIndexKey.createIndexKey("dom.elementClass");

  private static final DomElementClassIndex ourInstance = new DomElementClassIndex();

  public static DomElementClassIndex getInstance() {
    return ourInstance;
  }

  public boolean hasStubElementsOfType(@NotNull DomFileElement<?> domFileElement,
                                       @NotNull Class<? extends DomElement> clazz) {
    final VirtualFile file = domFileElement.getFile().getVirtualFile();
    if (!(file instanceof VirtualFileWithId)) return false;

    final String clazzName = clazz.getName();

    CommonProcessors.FindFirstProcessor<? super PsiFile> processor =
      new CommonProcessors.FindFirstProcessor<>();
    StubIndex.getInstance().processElements(KEY, clazzName,
                                            domFileElement.getFile().getProject(),
                                            GlobalSearchScope.fileScope(domFileElement.getFile()),
                                            PsiFile.class, 
                                            processor
    );

    return processor.isFound();
  }

  @Override
  public @NotNull StubIndexKey<String, PsiFile> getKey() {
    return KEY;
  }

  @Override
  public int getVersion() {
    return 0;
  }
}
