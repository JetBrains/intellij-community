package com.intellij.lang.xml;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.CompositeLanguage;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import static com.intellij.patterns.PlatformPatterns.or;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.impl.source.xml.behavior.CDATAOnAnyEncodedPolicy;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import com.intellij.psi.xml.*;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.refactoring.rename.RenameInputValidatorRegistry;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 10:59:22 AM
 * To change this template use File | Settings | File Templates.
 */
public class XMLLanguage extends CompositeLanguage {
  protected static final CDATAOnAnyEncodedPolicy CDATA_ON_ANY_ENCODED_POLICY = new CDATAOnAnyEncodedPolicy();
  protected static final EncodeEachSymbolPolicy ENCODE_EACH_SYMBOL_POLICY = new EncodeEachSymbolPolicy();

  static {
    RenameInputValidatorRegistry.getInstance().registerInputValidator(
      or(psiElement(XmlTag.class), psiElement(XmlAttribute.class), psiElement(XmlElementDecl.class), psiElement(XmlAttributeDecl.class)),
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

  public XMLLanguage() {
    this("XML", "text/xml");

    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExpicitExtension(this, new SingleLazyInstanceSyntaxHighlighterFactory() {
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
