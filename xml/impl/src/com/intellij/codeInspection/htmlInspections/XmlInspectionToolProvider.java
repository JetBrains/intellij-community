package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.xml.util.*;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnboundNsPrefixInspection;

/**
 * @author yole
 */
public class XmlInspectionToolProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
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
      XmlUnboundNsPrefixInspection.class
    };
  }
}
