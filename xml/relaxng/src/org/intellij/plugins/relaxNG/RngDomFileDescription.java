// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    registerNamespacePolicy("RELAX-NG", RelaxNgMetaDataContributor.RNG_NAMESPACE);
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
