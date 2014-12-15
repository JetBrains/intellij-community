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
package com.intellij.xml;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.editor.XmlFoldingSettings;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.*;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.dtd.DTDParserDefinition;
import com.intellij.lang.dtd.DtdSyntaxHighlighterFactory;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HTMLParserDefinition;
import com.intellij.lang.html.HtmlSyntaxHighlighterFactory;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xhtml.XHTMLParserDefinition;
import com.intellij.lang.xhtml.XhtmlSyntaxHighlighterFactory;
import com.intellij.lang.xml.*;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.XmlElementFactoryImpl;
import com.intellij.psi.impl.cache.impl.id.IdIndexers;
import com.intellij.psi.impl.cache.impl.idCache.XHtmlTodoIndexer;
import com.intellij.psi.impl.cache.impl.idCache.XmlIdIndexer;
import com.intellij.psi.impl.cache.impl.idCache.XmlTodoIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.xml.index.SchemaTypeInheritanceIndex;
import com.intellij.xml.index.XmlNamespaceIndex;
import com.intellij.xml.index.XmlTagNamesIndex;
import com.intellij.xml.util.XmlApplicationComponent;

/**
 * @author yole
 */
@SuppressWarnings("UnusedDeclaration") //upsource
public class XmlCoreEnvironment {
  public static class ApplicationEnvironment {
    public ApplicationEnvironment(CoreApplicationEnvironment appEnvironment) {
      appEnvironment.registerFileType(HtmlFileType.INSTANCE, "html;htm;sht;shtm;shtml");
      appEnvironment.registerFileType(XHtmlFileType.INSTANCE, "xhtml");
      appEnvironment.registerFileType(DTDFileType.INSTANCE, "dtd;ent;mod;elt");

      appEnvironment.registerFileType(XmlFileType.INSTANCE, "xml;xsd;tld;xsl;jnlp;wsdl;jhm;ant;xul;xslt;rng;fxml");

      SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(XMLLanguage.INSTANCE, new XmlSyntaxHighlighterFactory());
      SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(DTDLanguage.INSTANCE, new DtdSyntaxHighlighterFactory());
      SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(HTMLLanguage.INSTANCE, new HtmlSyntaxHighlighterFactory());
      SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(XHTMLLanguage.INSTANCE, new XhtmlSyntaxHighlighterFactory());

      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, XMLLanguage.INSTANCE, new XMLParserDefinition());
      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, DTDLanguage.INSTANCE, new DTDParserDefinition());
      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, HTMLLanguage.INSTANCE, new HTMLParserDefinition());
      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, XHTMLLanguage.INSTANCE, new XHTMLParserDefinition());

      appEnvironment.addExplicitExtension(IdIndexers.INSTANCE, XmlFileType.INSTANCE, new XmlIdIndexer());
      appEnvironment.addExplicitExtension(IdIndexers.INSTANCE, DTDFileType.INSTANCE, new XmlIdIndexer());
      appEnvironment.addExplicitExtension(TodoIndexers.INSTANCE, XmlFileType.INSTANCE, new XmlTodoIndexer());
      appEnvironment.addExplicitExtension(TodoIndexers.INSTANCE, DTDFileType.INSTANCE, new XmlTodoIndexer());
      appEnvironment.addExplicitExtension(TodoIndexers.INSTANCE, XHtmlFileType.INSTANCE, new XHtmlTodoIndexer());

      appEnvironment.addExtension(MetaDataContributor.EP_NAME, new XmlApplicationComponent());
      appEnvironment.addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new XmlNamespaceIndex());
      appEnvironment.addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new SchemaTypeInheritanceIndex());
      appEnvironment.addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new XmlTagNamesIndex());
      appEnvironment.addExtension(StandardResourceProvider.EP_NAME, new InternalResourceProvider());

      appEnvironment.registerApplicationComponent(PathMacros.class, new PathMacrosImpl());
      appEnvironment.registerApplicationService(ExternalResourceManager.class, new ExternalResourceManagerExImpl());
      appEnvironment.registerApplicationService(XmlFoldingSettings.class, new XmlFoldingSettings());
      Language[] myLanguages = new Language[]{XMLLanguage.INSTANCE, HTMLLanguage.INSTANCE, XHTMLLanguage.INSTANCE, DTDLanguage.INSTANCE};
      for (Language myLanguage : myLanguages) {
        appEnvironment.addExplicitExtension(LanguageFolding.INSTANCE, myLanguage, new XmlFoldingBuilder());
        appEnvironment.addExplicitExtension(LanguageFindUsages.INSTANCE, myLanguage, new XmlFindUsagesProvider());
        appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, myLanguage, new XmlASTFactory());
      }
    }

    protected ExternalResourceManagerEx createExternalResourceManager() {
      return new CoreExternalResourceManager();
    }
  }

  public static class ProjectEnvironment {
    public ProjectEnvironment(CoreProjectEnvironment projectEnvironment) {
      projectEnvironment.getProject().registerService(XmlElementFactory.class, new XmlElementFactoryImpl(projectEnvironment.getProject()));
      projectEnvironment.getProject().registerService(ExternalResourceManagerExImpl.class, new ProjectResources());
    }
  }
}
