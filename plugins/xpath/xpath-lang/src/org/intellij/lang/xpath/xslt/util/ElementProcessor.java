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
package org.intellij.lang.xpath.xslt.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class ElementProcessor<T extends PsiElement> implements ResolveUtil.XmlProcessor {
    private int myInclude;
    private boolean myIsCyclic;

    protected final T myRoot;

  private final Set<PsiElement> myHistory = ContainerUtil.<PsiElement>newIdentityTroveSet();

    public ElementProcessor(T root) {
        myRoot = root;
    }

    protected abstract void processTemplate(XmlTag tag);
    protected abstract void processVarOrParam(XmlTag tag);

    protected abstract boolean shouldContinue();
    protected abstract boolean followImport();

    protected boolean isInclude() {
        return myInclude > 0;
    }

    public boolean isCyclic() {
        return myIsCyclic;
    }

    public boolean process(XmlTag tag) {
        if (myHistory.contains(tag)) {
            myIsCyclic = true;
            return false;
        }
        myHistory.add(tag);

        if (XsltSupport.isVariableOrParam(tag)) {
            processVarOrParam(tag);
        } else if (XsltSupport.isTemplate(tag, false)) {
            processTemplate(tag);
        } else if (XsltSupport.isIncludeOrImport(tag)) {
            if (XsltSupport.isImport(tag) && !followImport()) {
                return shouldContinue();
            }
            final PsiFile containingFile = tag.getContainingFile();
            assert containingFile != null;
            PsiFile file = containingFile.getOriginalFile();

            final PsiFile psiFile = ResolveUtil.resolveFile(tag.getAttribute("href", null), file);
            if (psiFile != null && XsltSupport.isXsltFile(psiFile)) {
                processExternalFile(psiFile, tag);
            }
        } else {
          processTag(tag);
        }
        return shouldContinue();
    }

    protected void processTag(XmlTag tag) {
    }

    public void processExternalFile(PsiFile psiFile, XmlTag place) {
        final XmlDocument document = ((XmlFile)psiFile).getDocument();
        assert document != null;

        final XmlTag rootTag = document.getRootTag();
        assert rootTag != null;

        myInclude++;
        try {
            rootTag.processElements(new PsiElementProcessor() {
                public boolean execute(@NotNull PsiElement element) {
                    if (element instanceof XmlTag) {
                        return process((XmlTag)element);
                    }
                    return shouldContinue();
                }
            }, place);
        } finally {
            myInclude--;
        }
    }
}
