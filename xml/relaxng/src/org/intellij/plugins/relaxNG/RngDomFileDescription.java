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
package org.intellij.plugins.relaxNG;

import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import org.intellij.plugins.relaxNG.model.annotation.ModelAnnotator;
import org.intellij.plugins.relaxNG.xml.dom.*;

/**
* @author peter
*/
public class RngDomFileDescription<T> extends DomFileDescription<T> {
  public RngDomFileDescription(Class<T> elementClass, String rootTagName) {
    super(elementClass, rootTagName);
    registerNamespacePolicy("RELAX-NG", ApplicationLoader.RNG_NAMESPACE);
  }

  @Override
  public boolean isAutomaticHighlightingEnabled() {
    return true;
  }

  @Override
  public DomElementsAnnotator createAnnotator() {
    return new ModelAnnotator();
  }

  public static class RngGrammarDescription extends RngDomFileDescription<RngGrammar> {
    public RngGrammarDescription() {
      super(RngGrammar.class, "grammar");
    }
  }

  public static class RngElementDescription extends RngDomFileDescription<RngElement> {
    public RngElementDescription() {
      super(RngElement.class, "element");
    }
  }

  public static class RngChoiceDescription extends RngDomFileDescription<RngChoice> {
    public RngChoiceDescription() {
      super(RngChoice.class, "choice");
    }
  }

  public static class RngGroupDescription extends RngDomFileDescription<RngGroup> {
    public RngGroupDescription() {
      super(RngGroup.class, "group");
    }
  }

  public static class RngInterleaveDescription extends RngDomFileDescription<RngInterleave> {
    public RngInterleaveDescription() {
      super(RngInterleave.class, "interleave");
    }
  }
}
