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
package com.intellij.lang.xml;

import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TreePatcher;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class XmlTemplateTreePatcher implements TreePatcher {
  @Override
  public void insert(@NotNull CompositeElement parent, TreeElement anchorBefore, @NotNull OuterLanguageElement toInsert) {
    if(anchorBefore != null) {
      //[mike]
      //Nasty hack. Is used not to insert OuterLanguageElements before the first token of tag.
      //See GeneralJspParsingTest.testHtml6
      if (anchorBefore.getElementType() == XmlTokenType.XML_START_TAG_START) {
        anchorBefore = anchorBefore.getTreeParent();
      }

      anchorBefore.rawInsertBeforeMe((TreeElement)toInsert);
    }
    else parent.rawAddChildren((TreeElement)toInsert);
  }

}
