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
package com.intellij.properties;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.ide.IconProvider;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.editor.PropertiesFoldingBuilder;
import com.intellij.lang.properties.findUsages.PropertiesFindUsagesProvider;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.lang.properties.psi.PropertyKeyIndex;
import com.intellij.lang.properties.psi.impl.PropertiesASTFactory;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.lang.properties.refactoring.PropertiesRefactoringSettings;
import com.intellij.lang.properties.structureView.PropertiesSeparatorManager;
import com.intellij.lang.properties.xml.XmlPropertiesIconProvider;
import com.intellij.lang.properties.xml.XmlPropertiesIndex;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.impl.cache.impl.id.IdIndexers;
import com.intellij.psi.impl.cache.impl.idCache.PropertiesIdIndexer;
import com.intellij.psi.impl.cache.impl.idCache.PropertiesTodoIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.stubs.StubElementTypeHolderEP;
import com.intellij.psi.stubs.StubIndexExtension;
import com.intellij.util.indexing.FileBasedIndexExtension;

/**
 * @author Anna Bulenkova
 */
@SuppressWarnings("UnusedDeclaration") // upsource
public class PropertiesCoreEnvironment {
  public static class ApplicationEnvironment {
    public ApplicationEnvironment(CoreApplicationEnvironment appEnvironment) {
      appEnvironment.registerFileType(PropertiesFileType.INSTANCE, "properties");
      appEnvironment.addExplicitExtension(SyntaxHighlighterFactory.LANGUAGE_FACTORY, PropertiesLanguage.INSTANCE,
                                          new PropertiesSyntaxHighlighterFactory());
      appEnvironment.addExplicitExtension(LanguageParserDefinitions.INSTANCE, PropertiesLanguage.INSTANCE, new PropertiesParserDefinition());
      appEnvironment.addExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, new XmlPropertiesIndex());
      appEnvironment.addExtension(StubIndexExtension.EP_NAME, new PropertyKeyIndex());

      appEnvironment.registerApplicationService(PropertiesQuickFixFactory.class, new EmptyPropertiesQuickFixFactory());
      appEnvironment.registerApplicationService(PropertiesRefactoringSettings.class, new PropertiesRefactoringSettings());
      appEnvironment.addExplicitExtension(LanguageAnnotators.INSTANCE, PropertiesLanguage.INSTANCE, new PropertiesAnnotator());
      appEnvironment.addExplicitExtension(LanguageFindUsages.INSTANCE, PropertiesLanguage.INSTANCE, new PropertiesFindUsagesProvider());

      appEnvironment.addExplicitExtension(LanguageASTFactory.INSTANCE, PropertiesLanguage.INSTANCE, new PropertiesASTFactory());
      appEnvironment.addExplicitExtension(LanguageFolding.INSTANCE, PropertiesLanguage.INSTANCE, new PropertiesFoldingBuilder());
      appEnvironment.addExplicitExtension(ElementManipulators.INSTANCE, PropertyImpl.class, new PropertyManipulator());
      appEnvironment.addExplicitExtension(ElementManipulators.INSTANCE, PropertyKeyImpl.class, new PropertyKeyManipulator());
      appEnvironment.addExplicitExtension(ElementManipulators.INSTANCE, PropertyValueImpl.class, new PropertyValueManipulator());

      final StubElementTypeHolderEP stubElementTypeHolderBean = new StubElementTypeHolderEP();
      stubElementTypeHolderBean.holderClass = PropertiesElementTypes.class.getName();
      appEnvironment.addExtension(StubElementTypeHolderEP.EP_NAME, stubElementTypeHolderBean);

      appEnvironment.addExplicitExtension(LanguageCommenters.INSTANCE, PropertiesLanguage.INSTANCE, new PropertiesCommenter());
      appEnvironment.addExplicitExtension(IdIndexers.INSTANCE, PropertiesFileType.INSTANCE, new PropertiesIdIndexer());
      appEnvironment.addExplicitExtension(TodoIndexers.INSTANCE, PropertiesFileType.INSTANCE, new PropertiesTodoIndexer());

      appEnvironment.addExtension(IconProvider.EXTENSION_POINT_NAME, new XmlPropertiesIconProvider());
    }
  }

  public static class ProjectEnvironment {
    public ProjectEnvironment(CoreProjectEnvironment projectEnvironment) {
      projectEnvironment.getProject().registerService(PropertiesReferenceManager.class);
      projectEnvironment.getProject().registerService(PropertiesSeparatorManager.class);
      projectEnvironment.getProject().registerService(ResourceBundleManager.class);
    }
  }
}
