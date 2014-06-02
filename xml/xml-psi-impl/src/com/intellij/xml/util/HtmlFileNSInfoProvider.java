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
package com.intellij.xml.util;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class HtmlFileNSInfoProvider implements XmlFileNSInfoProvider {
  @Nullable
  @Override
  public String[][] getDefaultNamespaces(@NotNull XmlFile file) {
    return null;
  }

  @Override
  public boolean overrideNamespaceFromDocType(@NotNull XmlFile file) {
    return file.getFileType() == HtmlFileType.INSTANCE || file.getFileType() == XHtmlFileType.INSTANCE;
  }
}
