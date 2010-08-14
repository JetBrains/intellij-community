/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.template.FileTypeBasedContextType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

public class MavenLiveTemplateContextType extends FileTypeBasedContextType {
  public MavenLiveTemplateContextType() {
    super("MAVEN", "Maven", XmlFileType.INSTANCE);
  }

  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    return super.isInContext(file, offset) && MavenDomUtil.isMavenFile(file);
  }
}
