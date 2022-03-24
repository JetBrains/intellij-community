// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.*;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class DomHighlightingHelperImpl extends DomHighlightingHelper {
  public static final DomHighlightingHelperImpl INSTANCE = new DomHighlightingHelperImpl();
  private final GenericValueReferenceProvider myProvider = new GenericValueReferenceProvider();
  private final DomApplicationComponent myDomApplicationComponent = DomApplicationComponent.getInstance();

  @Override
  public void runAnnotators(DomElement element, DomElementAnnotationHolder holder, @NotNull Class<? extends DomElement> rootClass) {
    final DomElementsAnnotator annotator = myDomApplicationComponent.getAnnotator(rootClass);
    if (annotator != null) {
      annotator.annotate(element, holder);
    }
  }

  @Override
  @NotNull
  public List<DomElementProblemDescriptor> checkRequired(final DomElement element, final DomElementAnnotationHolder holder) {
    final Required required = element.getAnnotation(Required.class);
    if (required != null) {
      final XmlElement xmlElement = element.getXmlElement();
      if (xmlElement == null) {
        if (required.value()) {
          final String xmlElementName = element.getXmlElementName();
          String namespace = element.getXmlElementNamespace();
          if (element instanceof GenericAttributeValue) {
            return Collections.singletonList(holder.createProblem(element, XmlDomBundle.message(
              "dom.inspections.attribute.0.should.be.defined", xmlElementName),
                                                              new DefineAttributeQuickFix(xmlElementName, namespace)));
          }
          return Collections.singletonList(
            holder.createProblem(
              element,
              HighlightSeverity.ERROR,
              XmlDomBundle.message("dom.inspections.child.tag.0.should.be.defined", xmlElementName),
              new AddRequiredSubtagFix(xmlElementName, namespace)
            )
          );
        }
      }
      else if (element instanceof GenericDomValue) {
        return ContainerUtil.createMaybeSingletonList(checkRequiredGenericValue((GenericDomValue)element, required, holder));
      }
    }
    if (DomUtil.hasXml(element)) {
      final SmartList<DomElementProblemDescriptor> list = new SmartList<>();
      final DomGenericInfo info = element.getGenericInfo();
      for (final AbstractDomChildrenDescription description : info.getChildrenDescriptions()) {
        if (description instanceof DomCollectionChildDescription && description.getValues(element).isEmpty()) {
          final DomCollectionChildDescription childDescription = (DomCollectionChildDescription)description;
          final Required annotation = description.getAnnotation(Required.class);
          if (annotation != null && annotation.value()) {
            list.add(holder.createProblem(element, childDescription, XmlDomBundle.message("dom.inspections.child.tag.0.should.be.defined", ((DomCollectionChildDescription)description).getXmlElementName())));
          }
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public List<DomElementProblemDescriptor> checkResolveProblems(GenericDomValue element, final DomElementAnnotationHolder holder) {
    if (StringUtil.isEmpty(element.getStringValue())) {
      final Required required = element.getAnnotation(Required.class);
      if (required != null && !required.nonEmpty()) return Collections.emptyList();
    }

    final XmlElement valueElement = DomUtil.getValueElement(element);
    if (valueElement != null && !isSoftReference(element)) {
      final SmartList<DomElementProblemDescriptor> list = new SmartList<>();
      final PsiReference[] psiReferences = myProvider.getReferencesByElement(valueElement, new ProcessingContext());
      GenericDomValueReference domReference = ContainerUtil.findInstance(psiReferences, GenericDomValueReference.class);
      final Converter converter = WrappingConverter.getDeepestConverter(element.getConverter(), element);
      boolean hasBadResolve = false;
      if (domReference == null || !isDomResolveOK(element, domReference, converter)) {
        for (final PsiReference reference : psiReferences) {
          if (reference != domReference && hasBadResolve(reference)) {
            hasBadResolve = true;
            list.add(holder.createResolveProblem(element, reference));
          }
        }
        final boolean isResolvingConverter = converter instanceof ResolvingConverter;
        //noinspection unchecked
        if (!hasBadResolve &&
            (domReference != null || isResolvingConverter &&
                                     hasBadResolve(domReference = new GenericDomValueReference(element)))) {
          hasBadResolve = true;
          final String errorMessage = converter
            .getErrorMessage(element.getStringValue(), ConvertContextFactory.createConvertContext(
              DomManagerImpl.getDomInvocationHandler(element)));
          if (errorMessage != null) {
            list.add(holder.createResolveProblem(element, domReference));
          }
        }
      }
      if (!hasBadResolve && psiReferences.length == 0 && element.getValue() == null && !PsiTreeUtil.hasErrorElements(valueElement)) {
        final String errorMessage = converter
          .getErrorMessage(element.getStringValue(), ConvertContextFactory.createConvertContext(DomManagerImpl.getDomInvocationHandler(element)));
        if (errorMessage != null) {
          list.add(holder.createProblem(element, errorMessage));
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  private static boolean isDomResolveOK(GenericDomValue element, GenericDomValueReference domReference, Converter converter) {
    return !hasBadResolve(domReference) ||
           converter instanceof ResolvingConverter && ((ResolvingConverter<?>)converter).getAdditionalVariants(domReference.getConvertContext()).contains(element.getStringValue());
  }

  @Override
  @NotNull
  public List<DomElementProblemDescriptor> checkNameIdentity(DomElement element, final DomElementAnnotationHolder holder) {
    final String elementName = ElementPresentationManager.getElementName(element);
    if (StringUtil.isNotEmpty(elementName)) {
      final DomElement domElement = DomUtil.findDuplicateNamedValue(element, elementName);
      if (domElement != null) {
        final String typeName = ElementPresentationManager.getTypeNameForObject(element);
        final GenericDomValue genericDomValue = domElement.getGenericInfo().getNameDomElement(element);
        if (genericDomValue != null) {
          return Collections.singletonList(holder.createProblem(genericDomValue, DomUtil.getFile(domElement).equals(DomUtil.getFile(element))
                                                                 ? XmlDomBundle.message("dom.inspections.identity", typeName)
                                                                 : XmlDomBundle.message("dom.inspections.identity.in.other.file", typeName,
                                                                                        domElement.getXmlTag().getContainingFile()
                                                                                       .getName())));
        }
      }
    }
    return Collections.emptyList();
  }

  private static boolean hasBadResolve(PsiReference reference) {
    return XmlHighlightVisitor.hasBadResolve(reference, true);
  }

  private static boolean isSoftReference(GenericDomValue value) {
    final Resolve resolve = value.getAnnotation(Resolve.class);
    if (resolve != null && resolve.soft()) return true;

    final Convert convert = value.getAnnotation(Convert.class);
    if (convert != null && convert.soft()) return true;

    final Referencing referencing = value.getAnnotation(Referencing.class);
    return referencing != null && referencing.soft();

  }

  @Nullable
  private static DomElementProblemDescriptor checkRequiredGenericValue(final GenericDomValue child, final Required required,
                                                                       final DomElementAnnotationHolder annotator) {
    final String stringValue = child.getStringValue();
    if (stringValue == null) return null;

    if (required.nonEmpty() && isEmpty(child, stringValue)) {
      return annotator.createProblem(child, XmlDomBundle.message("dom.inspections.value.must.not.be.empty"));
    }
    if (required.identifier() && !isIdentifier(stringValue)) {
      return annotator.createProblem(child, XmlDomBundle.message("dom.inspections.value.must.be.identifier"));
    }
    return null;
  }

  private static boolean isIdentifier(final String s) {
    if (StringUtil.isEmptyOrSpaces(s)) return false;

    if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;

    for (int i = 1; i < s.length(); i++) {
      if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
    }

    return true;
  }

  private static boolean isEmpty(final GenericDomValue child, final String stringValue) {
    if (stringValue.trim().length() != 0) {
      return false;
    }
    if (child instanceof GenericAttributeValue) {
      final XmlAttributeValue value = ((GenericAttributeValue<?>)child).getXmlAttributeValue();
      if (value != null && value.getTextRange().isEmpty()) {
        return false;
      }
    }
    return true;
  }


  private static final class AddRequiredSubtagFix implements LocalQuickFix {
    private final String tagName;
    private final String tagNamespace;

    private AddRequiredSubtagFix(@NotNull String _tagName, @NotNull String _tagNamespace) {
      tagName = _tagName;
      tagNamespace = _tagNamespace;
    }

    @Override
    @NotNull
    public String getName() {
      return XmlDomBundle.message("dom.quickfix.insert.required.tag.text", tagName);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return XmlDomBundle.message("dom.quickfix.insert.required.tag.family");
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      XmlTag tag = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), XmlTag.class, false);
      if (tag != null) {
        doFix(tag);
      }
    }

    private void doFix(XmlTag parentTag) {
      parentTag.add(parentTag.createChildTag(tagName, tagNamespace, null, false));
    }
  }
}
