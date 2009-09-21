package com.intellij.xml.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlFile;

/**
 * @author yole
 */
public interface XmlIdContributor {
  ExtensionPointName<XmlIdContributor> EP_NAME = ExtensionPointName.create("com.intellij.xml.idContributor");

  boolean suppressExistingIdValidation(XmlFile file);
}
