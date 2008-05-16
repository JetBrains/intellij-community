/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.quickfix;

import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;

public class DeleteUnusedParameterFix extends DeleteUnusedElementBase<XsltParameter> {

    public DeleteUnusedParameterFix(String name, XsltParameter param) {
        super(name, param);
    }

    public String getType() {
        return "Parameter";
    }

    protected void deleteElement(@NotNull XsltParameter obj) throws IncorrectOperationException {
        final XsltTemplate template = XsltCodeInsightUtil.getTemplate(obj.getTag(), false);
        if (template == null || template.getMatchExpression() == null) {
            final SearchScope searchScope = obj.getResolveScope();
          for (PsiReference reference : ReferencesSearch.search(obj, searchScope, false)) {
                final XmlTag t = PsiTreeUtil.getContextOfType(reference.getElement(), XmlTag.class, true);
                if (t != null && XsltSupport.XSLT_NS.equals(t.getNamespace())) {
                    assert "with-param".equals(t.getLocalName());
                    t.delete();
                }
            }
        }
        super.deleteElement(obj);
    }
}
