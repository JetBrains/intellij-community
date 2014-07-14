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
package com.intellij.patterns;

import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class DomFilePattern<Self extends DomFilePattern<Self>> extends XmlFilePattern<Self> {
  public DomFilePattern(final Class<? extends DomElement> aClass) {
    super(new InitialPatternCondition<XmlFile>(XmlFile.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return o instanceof XmlFile && DomManager.getDomManager(((XmlFile)o).getProject()).getFileElement((XmlFile)o, aClass) != null;
      }
    });
  }

  public static class Capture extends DomFilePattern<Capture> {
    public Capture(Class<? extends DomElement> aClass) {
      super(aClass);
    }
  }
}
