package com.intellij.properties;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.lang.properties.psi.PropertyKeyIndex;
import com.intellij.lang.properties.xml.XmlPropertiesIndex;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.stubs.StubIndexExtension;
import com.intellij.util.indexing.FileBasedIndexExtension;

/**
 * @author Anna Bulenkova
 */
public class PropertiesCoreEnvironment {
  public static class ApplicationEnvironment {
    public ApplicationEnvironment(CoreApplicationEnvironment appEnvironment) {
      appEnvironment.registerFileType(PropertiesFileType.INSTANCE, "properties");
      SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(PropertiesLanguage.INSTANCE, new PropertiesSyntaxHighlighterFactory());
      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, PropertiesLanguage.INSTANCE, new PropertiesParserDefinition());
      appEnvironment.addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new XmlPropertiesIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new PropertyKeyIndex());
    }
  }

  public static class ProjectEnvironment {
    public ProjectEnvironment(CoreProjectEnvironment projectEnvironment) {
    }
  }
}
