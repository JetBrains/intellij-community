package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public class XmlEntityRefImpl extends XmlElementImpl implements XmlEntityRef {
  private static final Key<Map<String,CachedValue<XmlEntityDecl>>> XML_ENTITY_DECL_MAP = Key.create("XML_ENTITY_DECL_MAP");
  @NonNls private static final String GT_ENTITY = "&gt;";
  @NonNls private static final String QUOT_ENTITY = "&quot;";

  public XmlEntityRefImpl() {
    super(XML_ENTITY_REF);
  }

  public XmlEntityDecl resolve(PsiFile targetFile) {
    String text = getText();
    if (text.equals(GT_ENTITY) || text.equals(QUOT_ENTITY)) return null;
    final String entityName = text.substring(1, text.length() - 1);

    final PsiElement targetElement = targetFile != null ? (PsiElement)targetFile : this;
    Map<String, CachedValue<XmlEntityDecl>> map = targetElement.getUserData(XML_ENTITY_DECL_MAP);
    if (map == null){
      map = new HashMap<String,CachedValue<XmlEntityDecl>>();
      targetElement.putUserData(XML_ENTITY_DECL_MAP, map);
    }
    CachedValue<XmlEntityDecl> value = map.get(entityName);
    if (value == null) {
      if(getManager() == null){
        return resolveEntity(targetElement, entityName).getValue();
      }
      value = getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<XmlEntityDecl>() {
        public CachedValueProvider.Result<XmlEntityDecl> compute() {
          return resolveEntity(targetElement, entityName);
        }
      });


      map.put(entityName, value);
    }

    return value.getValue();
  }

  private CachedValueProvider.Result<XmlEntityDecl> resolveEntity(final PsiElement targetElement, final String entityName) {
    final List<PsiElement> deps = new ArrayList<PsiElement>();
    final XmlEntityDecl[] result = new XmlEntityDecl[]{null};

    PsiElementProcessor processor = new PsiElementProcessor() {
      public boolean execute(PsiElement element) {
        if (element instanceof XmlDoctype) {
          XmlDoctype xmlDoctype = (XmlDoctype)element;
          final String dtdUri = xmlDoctype.getDtdUri();
          if (dtdUri != null) {
            final XmlFile xmlFile = XmlUtil.findXmlFile(XmlUtil.getContainingFile(element), dtdUri);
            if (xmlFile != null) {
              if (xmlFile != targetElement) {
                deps.add(xmlFile);
                if(!PsiTreeUtil.processElements(xmlFile, this)) return false;
              }
            }
          }
          final XmlMarkupDecl markupDecl = xmlDoctype.getMarkupDecl();
          if (markupDecl != null) {
            if (!PsiTreeUtil.processElements(markupDecl, this)) return false;
          }
        }
        else if (element instanceof XmlEntityDecl) {
          XmlEntityDecl entityDecl = (XmlEntityDecl)element;
          final String declName = entityDecl.getName();
          if (declName.equals(entityName)) {
            result[0] = entityDecl;
            return false;
          }
        }

        return true;
      }
    };
    deps.add(targetElement);

    PsiTreeUtil.processElements(targetElement, processor);

    return new CachedValueProvider.Result<XmlEntityDecl>(result[0], deps.toArray(new Object[deps.size()]));
  }

  public XmlTag getParentTag() {
    final XmlElement parent = (XmlElement)getParent();
    if(parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  public XmlTagChild getNextSiblingInTag() {
    PsiElement nextSibling = getNextSibling();
    if(nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  public XmlTagChild getPrevSiblingInTag() {
    final PsiElement prevSibling = getPrevSibling();
    if(prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }

  public PsiReference[] getReferences() {
    return ResolveUtil.getReferencesFromProviders(this);
  }
}
