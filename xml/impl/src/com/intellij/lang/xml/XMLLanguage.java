package com.intellij.lang.xml;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.CompositeLanguage;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.impl.source.xml.behavior.CDATAOnAnyEncodedPolicy;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.xml.*;
import com.intellij.refactoring.rename.RenameInputValidatorRegistry;
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
      new OrFilter(new ClassFilter(XmlTag.class), new ClassFilter(XmlAttribute.class),
                   new ClassFilter(XmlElementDecl.class), new ClassFilter(XmlAttributeDecl.class)),
      new Condition<String>() {
        public boolean value(final String s) {
          return s.trim().matches("([\\d\\w\\_\\.\\-]+:)?[\\d\\w\\_\\.\\-]+");
        }
      });

    RenameInputValidatorRegistry.getInstance().registerInputValidator(
      new ClassFilter(XmlAttributeValue.class),
      new Condition<String>() {
        public boolean value(final String s) {
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
