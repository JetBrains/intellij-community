package com.intellij.psi.impl.source.html;

import com.intellij.openapi.util.Key;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.XmlElementDescriptor;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 1, 2005
 * Time: 11:17:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class ScriptSupportUtil {
  public static final Key<XmlTag[]> CachedScriptTagsKey = Key.create("script tags");

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
            final XmlElementDescriptor descriptor = ((XmlTag)element).getDescriptor();
            if (descriptor != null && "script".equals(descriptor.getName())) {
              scriptTags.add((XmlTag)element);
            }
          }
          return true;
        }
      }, true);
      
      myCachedScriptTags = scriptTags.toArray(new XmlTag[scriptTags.size()]);
      element.putUserData(CachedScriptTagsKey, myCachedScriptTags);
    }

    for (XmlTag tag : myCachedScriptTags) {
      final XmlTagChild[] children = tag.getValue().getChildren();
      for (XmlTagChild child : children) {
        if (!child.processDeclarations(processor, substitutor, null, place)) return false;
      }
    }

    return true;
  }
}
