/*
 * Copyright (c) 2008, Your Corporation. All Rights Reserved.
 */

package org.intellij.lang.xpath.xslt.impl.references;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.impl.XsltIncludeIndex;
import org.intellij.lang.xpath.xslt.psi.XsltApplyTemplates;
import org.intellij.lang.xpath.xslt.psi.XsltCallTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.util.ArgumentMatcher;
import org.intellij.lang.xpath.xslt.util.MatchTemplateMatcher;
import org.intellij.lang.xpath.xslt.util.NamedTemplateMatcher;
import org.intellij.lang.xpath.xslt.util.ParamMatcher;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

public class XsltReferenceProvider extends PsiReferenceProvider {
    private static final Key<CachedValue<PsiReference[]>> CACHED_XSLT_REFS = Key.create("CACHED_XSLT_REFS");

    private final CachedValuesManager myCacheManager;
    private final XsltElementFactory myXsltElementFactory = XsltElementFactory.getInstance();

    public XsltReferenceProvider(Project project) {
      myCacheManager = CachedValuesManager.getManager(project);
    }

    @NotNull
    public PsiReference[] getReferencesByElement(@NotNull PsiElement e, @NotNull ProcessingContext context) {
        final PsiElement element = e.getParent();
        if (element instanceof XmlAttribute) {
            final XmlAttribute attribute = (XmlAttribute)element;

            CachedValue<PsiReference[]> cachedValue = attribute.getUserData(CACHED_XSLT_REFS);
            if (cachedValue == null) {
                cachedValue = myCacheManager.createCachedValue(new ReferenceProvider(attribute), false);
                attribute.putUserData(CACHED_XSLT_REFS, cachedValue);
            }

            final PsiReference[] value = cachedValue.getValue();
            assert value != null;
            return value;
        } else {
            return PsiReference.EMPTY_ARRAY;
        }
    }

    private class ReferenceProvider implements CachedValueProvider<PsiReference[]> {
        private final XmlAttribute myAttribute;

        ReferenceProvider(XmlAttribute attribute) {
            myAttribute = attribute;
        }

        public Result<PsiReference[]> compute() {
            final PsiReference[] referencesImpl = getReferencesImpl(myAttribute);
            final Object[] refs = new PsiElement[referencesImpl.length];
            for (int i = 0; i < refs.length; i++) {
                refs[i] = referencesImpl[i].getElement();
            }
            return new Result<PsiReference[]>(referencesImpl, ArrayUtil.append(refs, myAttribute.getValueElement()));
        }

        private PsiReference[] getReferencesImpl(final XmlAttribute attribute) {
            final PsiReference[] psiReferences;
            final XmlTag tag = attribute.getParent();

            if (XsltSupport.isTemplateCallName(attribute)) {
                psiReferences = new PsiReference[]{ new TemplateReference(attribute) };
            } else if (XsltSupport.isTemplateCallParamName(attribute)) {
                final String paramName = attribute.getValue();
                final XmlTag templateCall = PsiTreeUtil.getParentOfType(tag, XmlTag.class);

                if (templateCall != null) {
                    if (XsltSupport.isTemplateCall(templateCall)) {
                        final XsltCallTemplate call = myXsltElementFactory.wrapElement(templateCall, XsltCallTemplate.class);
                        final ResolveUtil.Matcher matcher = new MyParamMatcher(paramName, call);
                        psiReferences = new PsiReference[]{ new AttributeReference(attribute, matcher, true) };
                    } else if (XsltSupport.isApplyTemplates(templateCall)) {
                        final XsltApplyTemplates call = myXsltElementFactory.wrapElement(templateCall, XsltApplyTemplates.class);
                        final ResolveUtil.Matcher matcher = new MyParamMatcher2(paramName, call);
                        psiReferences = new PsiReference[]{ new ParamReference(attribute, matcher) };
                    } else {
                        psiReferences = PsiReference.EMPTY_ARRAY;
                    }
                } else {
                    psiReferences = PsiReference.EMPTY_ARRAY;
                }
            } else if (XsltSupport.isParam(attribute) && isInsideUnnamedTemplate(tag)) {
                final XsltParameter myParam = myXsltElementFactory.wrapElement(tag, XsltParameter.class);
                psiReferences = new PsiReference[]{ new MySelfReference(attribute, myParam) };
            } else if (XsltSupport.isVariableOrParamName(attribute) || XsltSupport.isTemplateName(attribute)) {
                final XsltElement myElement = myXsltElementFactory.wrapElement(tag, XsltElement.class);
                psiReferences = new PsiReference[]{ new SelfReference(attribute, myElement) };
            } else if (XsltSupport.isIncludeOrImportHref(attribute)) {
                final String href = attribute.getValue();
                final String resourceLocation = ExternalResourceManager.getInstance().getResourceLocation(href);
                //noinspection StringEquality
                if (href == resourceLocation) {
                    // not a configured external resource
                    if (href.indexOf("://") == -1) {
                        // a local file reference
                        final FileReferenceSet filereferenceset = new FileReferenceSet(
                                href,
                                attribute.getValueElement(), 1, XsltReferenceProvider.this, true);
                        psiReferences = filereferenceset.getAllReferences();
                    } else {
                        // external, but unknown resource
                        psiReferences = new PsiReference[]{ new ExternalResourceReference(attribute) };
                    }
                } else {
                    // external, known resource
                    psiReferences = new PsiReference[]{ new ExternalResourceReference(attribute) };
                }
            } else if (XsltSupport.isMode(attribute)) {
                psiReferences = ModeReference.create(attribute, XsltSupport.isTemplate(tag, false));
            } else {
                psiReferences = PsiReference.EMPTY_ARRAY;
            }

            return psiReferences;
        }

        private class MySelfReference extends SelfReference {
            private final XsltParameter myParam;
            private final XmlTag myTag;

            public MySelfReference(XmlAttribute attribute, XsltParameter param) {
                super(attribute, param);
                myParam = param;
                myTag = param.getTag();
    }


            public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
                if (!newElementName.equals(myParam.getName())) {
                    myParam.setName(newElementName);
                }
                final XmlAttribute attribute = myParam.getNameAttribute();
                assert attribute != null;
                //noinspection ConstantConditions
                return attribute.getValueElement();
            }

            public boolean isReferenceTo(PsiElement element) {
                // self-reference is only a trick to enable rename/find usages etc. but it shouldn't actually
                // refer to itself because this would list the element to be renamed/searched for twice
                assert !super.isReferenceTo(element);

                if (element == myParam) return false;
                if (!(element instanceof XsltParameter)) return false;

                final XsltParameter param = ((XsltParameter)element);
                final String name = param.getName();
                if (name == null || !name.equals(myParam.getName())) return false;

                final XsltTemplate template = XsltCodeInsightUtil.getTemplate(myTag, false);
                final XsltTemplate myTemplate = XsltCodeInsightUtil.getTemplate(param.getTag(), false);
                if (template == myTemplate) return true;
                if (template == null || myTemplate == null) return false;

                if (!Comparing.equal(template.getMode(), myTemplate.getMode())) {
                    return false;
                }

                final XmlFile xmlFile = (XmlFile)element.getContainingFile();
                final XmlFile myFile = (XmlFile)myParam.getContainingFile();
                if (myFile == xmlFile) return true;

                return XsltIncludeIndex.isReachableFrom(myFile, xmlFile);
            }
        }
    }

    private static boolean isInsideUnnamedTemplate(XmlTag tag) {
        final XmlTag t = XsltCodeInsightUtil.getTemplateTag(tag, false, false);
        return t != null && t.getAttribute("name", null) == null;
    }

    static class MyParamMatcher extends NamedTemplateMatcher {
        private final XsltCallTemplate myCall;
        private final String myParamName;
        private String[] myExcludedNames = ArrayUtil.EMPTY_STRING_ARRAY;

        MyParamMatcher(String paramName, XsltCallTemplate call) {
            super(XsltCodeInsightUtil.getDocument(call), call.getTemplateName());
            myCall = call;
            myParamName = paramName;
        }

        private MyParamMatcher(String paramName, XsltCallTemplate call, String[] excludedNames) {
            super(getDocument(call), call.getTemplateName());
            myCall = call;
            myParamName = paramName;
            myExcludedNames = excludedNames;
        }

        private static XmlDocument getDocument(XsltCallTemplate call) {
            final XsltTemplate template = call.getTemplate();
            return XsltCodeInsightUtil.getDocument(template != null ? template : call);
        }

        @Override
        protected ResolveUtil.Matcher changeDocument(XmlDocument document) {
            return new MyParamMatcher(myParamName, myCall, myExcludedNames);
        }

        @Override
        protected Result matchImpl(XmlTag element) {
            if (matches(element)) {
                return Result.create(new ParamMatcher(element, myExcludedNames, myParamName));
            }
            return null;
        }

        @Override
        public ResolveUtil.Matcher variantMatcher() {
            final PsiElement[] suppliedArgs = ResolveUtil.collect(new ArgumentMatcher(myCall));
            final String[] excludedNames = new String[suppliedArgs.length];
            for (int i = 0; i < suppliedArgs.length; i++) {
                excludedNames[i] = ((XmlTag)suppliedArgs[i]).getAttributeValue("name");
            }
            return new MyParamMatcher(null, myCall, excludedNames);
        }
    }

    static class MyParamMatcher2 extends MatchTemplateMatcher {
        private final String myParamName;
        private final XsltApplyTemplates myCall;
        private String[] myExcludedNames = ArrayUtil.EMPTY_STRING_ARRAY;

        MyParamMatcher2(String paramName, XsltApplyTemplates call) {
            super(XsltCodeInsightUtil.getDocument(call), call.getMode());
            myParamName = paramName;
            myCall = call;
        }

        private MyParamMatcher2(String paramName, XsltApplyTemplates call, String[] excludedNames) {
            this(paramName, call);
            myExcludedNames = excludedNames;
        }

        @Override
        protected Result matchImpl(XmlTag element) {
            if (matches(element)) {
                return Result.create(new ParamMatcher(element, myExcludedNames, myParamName));
            }
            return null;
        }

        @Override
        protected ResolveUtil.Matcher changeDocument(XmlDocument document) {
            return new MyParamMatcher2(myParamName, myCall);
        }

        @Override
        public ResolveUtil.Matcher variantMatcher() {
            final PsiElement[] suppliedArgs = ResolveUtil.collect(new ArgumentMatcher(myCall));
            final String[] excludedNames = new String[suppliedArgs.length];
            for (int i = 0; i < suppliedArgs.length; i++) {
                excludedNames[i] = ((XmlTag)suppliedArgs[i]).getAttributeValue("name");
            }
            return new MyParamMatcher2(null, myCall, excludedNames);
        }
    }
}
