/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.xml;

import com.intellij.psi.FileViewProviderFactory;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.Language;

/**
 * @author yole
 */
public class XmlFileViewProviderFactory implements FileViewProviderFactory {
  public FileViewProvider createFileViewProvider(final VirtualFile file, final Language language, final PsiManager manager, final boolean physical) {
    if (SingleRootFileViewProvider.isTooLarge(file)) {
      return new SingleRootFileViewProvider(manager, file, physical);
    }

    return new XmlFileViewProvider(manager, file, physical, (XMLLanguage)language);
  }
}