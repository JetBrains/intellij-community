// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.*;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.xml.util.*;
import org.jetbrains.annotations.NotNull;


public class XmlInspectionToolProvider implements InspectionToolProvider {
  @Override
  public Class @NotNull [] getInspectionClasses() {
    return new Class[] {
      CheckTagEmptyBodyInspection.class,
      CheckDtdReferencesInspection.class,
      CheckEmptyTagInspection.class,
      CheckValidXmlInScriptBodyInspection.class,
      CheckXmlFileWithXercesValidatorInspection.class,
      XmlDuplicatedIdInspection.class,
      RequiredAttributesInspection.class,
      HtmlExtraClosingTagInspection.class,
      XmlWrongRootElementInspection.class,
      HtmlUnknownTagInspection.class,
      HtmlUnknownAttributeInspection.class,
      XmlUnboundNsPrefixInspection.class,
      XmlUnusedNamespaceInspection.class,
      XmlHighlightVisitorBasedInspection.class,
      XmlPathReferenceInspection.class,
      XmlUnresolvedReferenceInspection.class,
      HtmlUnknownTargetInspection.class,
      HtmlUnknownAnchorTargetInspection.class
    };
  }
}
