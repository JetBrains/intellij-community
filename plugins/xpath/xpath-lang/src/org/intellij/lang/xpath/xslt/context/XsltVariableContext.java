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
package org.intellij.lang.xpath.xslt.context;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import org.intellij.lang.xpath.context.VariableContext;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.impl.XsltIncludeIndex;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.quickfix.CreateParameterFix;
import org.intellij.lang.xpath.xslt.quickfix.CreateVariableFix;
import org.intellij.lang.xpath.xslt.util.ElementProcessor;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class XsltVariableContext implements VariableContext<XsltVariable> {
    public static final XsltVariableContext INSTANCE = new XsltVariableContext();
    
    private final ResolveCache.Resolver RESOLVER = new ResolveCache.Resolver() {
        @Nullable
        public PsiElement resolve(@NotNull PsiReference psiReference, boolean incompleteCode) {
            return resolveInner((XPathVariableReference)psiReference);
        }
    };

    @NotNull
    public XsltVariable[] getVariablesInScope(XPathElement element) {
        final XmlTag context = getContextTagImpl(element);
        final VariantsProcessor processor = new VariantsProcessor(context);

        ResolveUtil.treeWalkUp(processor, context);
        processForwardGlobals(context, processor);
        return processor.getResult();
    }

    public XPathVariable resolve(final XPathVariableReference reference) {
        return (XPathVariable) ResolveCache.getInstance(reference.getProject()).resolveWithCaching(reference, RESOLVER, false, false);
    }

    @Nullable
    private XPathVariable resolveInner(XPathVariableReference reference) {
        final XmlTag context = getContextTagImpl(reference);
        final VariableResolveProcessor processor = new VariableResolveProcessor(reference.getReferencedName(), context);

        final XPathVariable variable = (XPathVariable)ResolveUtil.treeWalkUp(processor, context);
        if (variable != null) {
          return variable;
        }
        if (!processForwardGlobals(context, processor)) {
          final XmlFile file = PsiTreeUtil.getParentOfType(context, XmlFile.class, true);
          if (file != null) {
            XsltIncludeIndex.processBackwardDependencies(file, xmlFile -> {
              processor.processExternalFile(xmlFile, context);
              return processor.shouldContinue();
            });
          }
        }
        return (XPathVariable)processor.getResult();
    }

    private static boolean processForwardGlobals(XmlTag context, VariableProcessor processor) {
      while (context != null && !XsltSupport.isTopLevelElement(context)) {
        context = context.getParentTag();
      }
      while (context != null && processor.shouldContinue()) {
        processor.process(context);
        context = PsiTreeUtil.getNextSiblingOfType(context, XmlTag.class);
      }
      return !processor.shouldContinue();
    }

    @Nullable
    protected XmlTag getContextTagImpl(XPathElement element) {
        return PsiTreeUtil.getContextOfType(element, XmlTag.class, true);
    }

    @NotNull
    public IntentionAction[] getUnresolvedVariableFixes(XPathVariableReference reference) {
        return new IntentionAction[] {
                new CreateVariableFix(reference),
                new CreateParameterFix(reference)
        };
    }

    public boolean isReferenceTo(PsiElement element, XPathVariableReference reference) {
        if (element instanceof XsltParameter) {
            final XsltTemplate template = XsltCodeInsightUtil.getTemplate(element, false);
            if (template == null || template.getMatchExpression() == null) return false;

            final XPathVariable t = reference.resolve();
            final PsiReference[] references = element.getReferences();
            for (PsiReference r : references) {
                if (r.isReferenceTo(t)) return true;
            }
        }
        return false;
    }

    public boolean canResolve() {
        return true;
    }

    static abstract class VariableProcessor extends ElementProcessor<XmlTag> {
        public VariableProcessor(XmlTag context) {
            super(context);
        }

        protected boolean followImport() {
            return true;
        }

        protected final void processTemplate(XmlTag tag) {
            // not interested
        }

        protected abstract void processVarOrParamImpl(XmlTag tag);

        protected final void processVarOrParam(XmlTag tag) {
            if (tag != myRoot) {
                processVarOrParamImpl(tag);
            }
        }

        protected abstract boolean shouldContinue();
    }

    static class VariantsProcessor extends VariableProcessor {
        private final List<XsltVariable> myNames = new ArrayList<>();

        public VariantsProcessor(XmlTag context) {
            super(context);
        }

        public XsltVariable[] getResult() {
            return myNames.toArray(new XsltVariable[myNames.size()]);
        }

        protected void processVarOrParamImpl(XmlTag tag) {
            if (XsltSupport.isVariableOrParam(tag)) {
                myNames.add(XsltElementFactory.getInstance().wrapElement(tag, XsltVariable.class));
            }
        }

        protected boolean shouldContinue() {
            return true;
        }
    }

    static class VariableResolveProcessor extends VariableProcessor implements ResolveUtil.ResolveProcessor {
        private final String myName;
        private PsiElement myResult = null;

        public VariableResolveProcessor(final String name, XmlTag context) {
            super(context);
            myName = name;
        }

        public PsiElement getResult() {
            return myResult;
        }

        protected boolean shouldContinue() {
            return myResult == null;
        }

        protected void processVarOrParamImpl(XmlTag tag) {
            if (XsltSupport.isVariableOrParam(tag)) {
                final String name = tag.getAttributeValue("name");
                if (myName.equals(name)) {
                    myResult = XsltElementFactory.getInstance().wrapElement(tag, XsltVariable.class);
                }
            }
        }
    }
}
