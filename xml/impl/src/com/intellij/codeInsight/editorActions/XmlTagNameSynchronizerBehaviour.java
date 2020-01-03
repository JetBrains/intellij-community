package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.actions.validate.ValidateXmlHandler;
import org.jetbrains.annotations.NotNull;

public interface XmlTagNameSynchronizerBehaviour {
  ExtensionPointName<XmlTagNameSynchronizerBehaviour> EP_NAME = ExtensionPointName.create("com.intellij.xml.tagNameSynchronizerBehaviour");

  boolean isApplicable(@NotNull PsiFile file);
  boolean isValidTagNameChar(char c);
}
