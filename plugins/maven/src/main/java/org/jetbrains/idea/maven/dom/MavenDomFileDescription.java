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
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.annotator.MavenDomAnnotator;

public abstract class MavenDomFileDescription<T> extends DomFileDescription<T> {
  public MavenDomFileDescription(Class<T> rootElementClass, String rootTagName) {
    super(rootElementClass, rootTagName);
  }

  public boolean isMyFile(@NotNull XmlFile file, final Module module) {
    return MavenDomUtil.isMavenFile(file) && super.isMyFile(file, module);
  }

  @Override
  public DomElementsAnnotator createAnnotator() {
    return new MavenDomAnnotator();
  }
}
