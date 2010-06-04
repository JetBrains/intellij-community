/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.xml;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.CompositeLanguage;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.impl.source.xml.behavior.CDATAOnAnyEncodedPolicy;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.refactoring.rename.RenameInputValidatorRegistry;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.or;
import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author max
 */
public class XMLLanguage extends CompositeLanguage {

  public final static XMLLanguage INSTANCE = new XMLLanguage();

  protected static final CDATAOnAnyEncodedPolicy CDATA_ON_ANY_ENCODED_POLICY = new CDATAOnAnyEncodedPolicy();
  protected static final EncodeEachSymbolPolicy ENCODE_EACH_SYMBOL_POLICY = new EncodeEachSymbolPolicy();

  static {
    RenameInputValidatorRegistry.getInstance().registerInputValidator(
      or(
        XmlPatterns.xmlTag().withMetaData(
          or(PlatformPatterns.instanceOf(XmlElementDescriptor.class),
             PlatformPatterns.instanceOf(XmlAttributeDescriptor.class))
        ),
        psiElement(XmlElementDecl.class),
        psiElement(XmlAttributeDecl.class),
        XmlPatterns.xmlTag().withDescriptor(
          or(PlatformPatterns.instanceOf(XmlElementDescriptor.class),
             PlatformPatterns.instanceOf(XmlAttributeDescriptor.class))
        )
      ),
      new RenameInputValidator() {
        public boolean isInputValid(final String newName, final PsiElement element, final ProcessingContext context) {
          return newName.trim().matches("([\\d\\w\\_\\.\\-]+:)?[\\d\\w\\_\\.\\-]+");
        }
      });

    RenameInputValidatorRegistry.getInstance().registerInputValidator(
      psiElement(XmlAttributeValue.class), new RenameInputValidator() {

      public boolean isInputValid(final String newName, final PsiElement element, final ProcessingContext context) {
        return true;
      }
    });
  }

  private XMLLanguage() {
    this("XML", "text/xml");

    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(this, new SingleLazyInstanceSyntaxHighlighterFactory() {
      @NotNull
      protected SyntaxHighlighter createHighlighter() {
        return new XmlFileHighlighter();
      }
    });
  }

  protected XMLLanguage(@NonNls String name, @NonNls String... mime) {
    super(name, mime);
  }


  public XmlPsiPolicy getPsiPolicy() {
    return CDATA_ON_ANY_ENCODED_POLICY;
  }
}
