/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.completion;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.util.PlatformIcons;
import org.intellij.lang.xpath.psi.XPathNodeTest;
import org.jetbrains.annotations.NotNull;

class NodeLookup extends AbstractLookup {
  private final XPathNodeTest.PrincipalType principalType;

  NodeLookup(String name, XPathNodeTest.PrincipalType principalType) {
    super(name, name);
    this.principalType = principalType;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setIcon(principalType == XPathNodeTest.PrincipalType.ATTRIBUTE ? PlatformIcons.ANNOTATION_TYPE_ICON : PlatformIcons.XML_TAG_ICON);
  }
}
