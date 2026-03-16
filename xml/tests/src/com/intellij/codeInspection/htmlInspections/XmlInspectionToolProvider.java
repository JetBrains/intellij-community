// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.HtmlUnknownAnchorTargetInspection;
import com.intellij.codeInsight.daemon.impl.analysis.HtmlUnknownTargetInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitorBasedInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnboundNsPrefixInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnusedNamespaceInspection;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import com.intellij.xml.util.CheckEmptyTagInspection;
import com.intellij.xml.util.CheckTagEmptyBodyInspection;
import com.intellij.xml.util.CheckValidXmlInScriptBodyInspection;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;
import com.intellij.xml.util.XmlDuplicatedIdInspection;
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
