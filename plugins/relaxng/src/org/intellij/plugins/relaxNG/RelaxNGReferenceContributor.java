package org.intellij.plugins.relaxNG;

import com.intellij.patterns.StringPattern;
import com.intellij.patterns.XmlNamedElementPattern;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.position.PatternFilter;
import com.intellij.xml.util.XmlUtil;
import org.intellij.plugins.relaxNG.references.PrefixReferenceProvider;

import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.patterns.XmlPatterns.xmlAttribute;
import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;
import static com.intellij.patterns.XmlPatterns.xmlTag;

/**
 * @author peter
 */
public class RelaxNGReferenceContributor extends PsiReferenceContributor {
  private static final XmlNamedElementPattern RNG_TAG_PATTERN = xmlTag().withNamespace(string().equalTo(ApplicationLoader.RNG_NAMESPACE));

  private static final XmlNamedElementPattern.XmlAttributePattern NAME_ATTR_PATTERN = xmlAttribute("name");
  private static final StringPattern ELEMENT_OR_ATTRIBUTE_PATTERN = string().oneOf("element", "attribute");

  private static final XmlNamedElementPattern.XmlAttributePattern NAME_PATTERN = NAME_ATTR_PATTERN.withParent(
    RNG_TAG_PATTERN.withLocalName(ELEMENT_OR_ATTRIBUTE_PATTERN));

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, new String[]{
      "name"
    }, new PatternFilter(xmlAttributeValue().withParent(NAME_PATTERN)), true, new PrefixReferenceProvider());

//    final XmlAttributeValuePattern id = xmlAttributeValue().withParent(xmlAttribute()).with(IdRefProvider.HAS_ID_REF_TYPE);
//    final XmlAttributeValuePattern idref = xmlAttributeValue().withParent(xmlAttribute()).with(IdRefProvider.HAS_ID_TYPE);
//    registry.registerXmlAttributeValueReferenceProvider(null, new PatternFilter(or(id, idref)), false, new IdRefProvider());

  }
}
