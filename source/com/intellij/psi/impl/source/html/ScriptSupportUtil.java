package com.intellij.psi.impl.source.html;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiFile;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 1, 2005
 * Time: 11:17:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class ScriptSupportUtil {
  private static final Key<XmlTag[]> CachedScriptTagsKey = Key.create("script tags");
  private static final Key<String> ProcessingDeclarationsKey = Key.create("processingDeclarationd");
  private static final @NonNls String SCRIPT_TAG = "script";

  public static void clearCaches(XmlFile element) {
    element.putUserData(CachedScriptTagsKey,null);
  }

  public static boolean processDeclarations(XmlFile element, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    XmlTag[] myCachedScriptTags = element.getUserData(CachedScriptTagsKey);

    if (myCachedScriptTags == null) {
      final List<XmlTag> scriptTags = new ArrayList<XmlTag>();
      XmlUtil.processXmlElements(
        HtmlUtil.getRealXmlDocument(element.getDocument()),
        new PsiElementProcessor() {
        public boolean execute(final PsiElement element) {
          if (element instanceof XmlTag) {
            final XmlTag tag = (XmlTag)element;
            
            if (SCRIPT_TAG.equalsIgnoreCase(tag.getName())) {
              final XmlElementDescriptor descriptor = tag.getDescriptor();
              if (descriptor != null && SCRIPT_TAG.equals(descriptor.getName())) {
                scriptTags.add(tag);
              }
            }
          }
          return true;
        }
      }, true);

      myCachedScriptTags = scriptTags.toArray(new XmlTag[scriptTags.size()]);
      element.putUserData(CachedScriptTagsKey, myCachedScriptTags);
    }

    if (element.getUserData(ProcessingDeclarationsKey) != null) return true;
    
    try {
      element.putUserData(ProcessingDeclarationsKey, "");

      for (XmlTag tag : myCachedScriptTags) {
        final XmlTagChild[] children = tag.getValue().getChildren();
        for (XmlTagChild child : children) {
          if (!child.processDeclarations(processor, substitutor, null, place)) return false;
        }

        if(tag.getAttributeValue("src") != null) {
          final XmlAttribute attribute = tag.getAttribute("src", null);

          if (attribute != null) {
            final XmlAttributeValue valueElement = attribute.getValueElement();

            if (valueElement != null) {
              final PsiReference[] references = valueElement.getReferences();

              if (references.length > 0) {
                final PsiElement psiElement = references[references.length - 1].resolve();

                if (psiElement instanceof PsiFile && psiElement.isValid()) {
                  if(!psiElement.processDeclarations(processor, substitutor, null, place))
                    return false;
                }
              }
            }
          }
        }
      }
    } finally {
      element.putUserData(ProcessingDeclarationsKey, null);
    }

    return true;
  }
}
