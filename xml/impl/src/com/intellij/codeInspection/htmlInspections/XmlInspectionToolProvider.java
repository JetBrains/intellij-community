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
      CheckImageSizeInspection.class,
      CheckTagEmptyBodyInspection.class,
      CheckDtdReferencesInspection.class,
      CheckEmptyScriptTagInspection.class,
      CheckValidXmlInScriptBodyInspection.class,
      CheckXmlFileWithXercesValidatorInspection.class,
      XmlDuplicatedIdInspection.class,
      RequiredAttributesInspection.class,
      HtmlExtraClosingTagInspection.class,
      XmlWrongRootElementInspection.class,
      HtmlUnknownTagInspection.class,
      HtmlUnknownAttributeInspection.class,
      HtmlDeprecatedTagInspection.class,
      XmlUnboundNsPrefixInspection.class
    };
  }
}
