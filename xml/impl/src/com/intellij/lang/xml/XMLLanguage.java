package com.intellij.lang.xml;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.CompositeLanguage;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import static com.intellij.patterns.PlatformPatterns.or;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.patterns.XmlPatterns;
import com.intellij.patterns.PlatformPatterns;
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
import com.intellij.xml.XmlElementDescriptor;
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

  public final static XMLLanguage INSTANCE = new XMLLanguage();

  protected static final CDATAOnAnyEncodedPolicy CDATA_ON_ANY_ENCODED_POLICY = new CDATAOnAnyEncodedPolicy();
  protected static final EncodeEachSymbolPolicy ENCODE_EACH_SYMBOL_POLICY = new EncodeEachSymbolPolicy();

  static {
    RenameInputValidatorRegistry.getInstance().registerInputValidator(
      or(XmlPatterns.xmlTag().withMetaData(PlatformPatterns.instanceOf(XmlElementDescriptor.class)), psiElement(XmlElementDecl.class), psiElement(XmlAttributeDecl.class)),
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
