/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.MacroFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.impl.XmlElementsGroupModel;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class XmlSmartCompletionProvider {

  public void complete(CompletionParameters parameters, CompletionResultSet result, PsiElement element) {
    if (!XmlCompletionContributor.isXmlNameCompletion(parameters)) {
      return;
    }
    result.stopHere();
    if (!(element.getParent() instanceof XmlTag)) {
      return;
    }
    final XmlTag tag = (XmlTag)element.getParent();
    final XmlTag parentTag = tag.getParentTag();
    Application application = ApplicationManager.getApplication();
    final XmlElementDescriptor parentDescriptor = application.runReadAction(new NullableComputable<XmlElementDescriptor>() {
      public XmlElementDescriptor compute() {
        return parentTag.getDescriptor();
      }
    });
    if (parentDescriptor == null) return;
    final XmlElementsGroup topGroup = application.runReadAction(new NullableComputable<XmlElementsGroup>() {
      @Override
      public XmlElementsGroup compute() {
        return parentDescriptor.getTopGroup();
      }
    });
    if (topGroup == null) return;

    XmlElementsGroupModel model = new XmlElementsGroupModel(topGroup);
    Set<XmlElementsGroup> anchor = null;
    Set<XmlElementsGroup> existing = new HashSet<XmlElementsGroup>();
    XmlTag[] subTags = parentTag.getSubTags();
    for (XmlTag subTag : subTags) {
      if (subTag == tag) {
        if (anchor == null) anchor = Collections.emptySet();
        continue;
      }
      XmlElementsGroup group = model.findGroup(subTag);
      if (group != null) {
        List<XmlElementsGroup> allAncestors = getAllAncestors(group);
        existing.addAll(allAncestors);
        if (anchor == null) {
          anchor = new HashSet<XmlElementsGroup>();
          anchor.addAll(allAncestors);
        }
      }
    }

    final List<XmlElementDescriptor> descriptors = new ArrayList<XmlElementDescriptor>();
    processGroup(topGroup, descriptors, anchor, existing, 1);

    result.addAllElements(ContainerUtil.map(descriptors, new Function<XmlElementDescriptor, LookupElement>() {
      @Override
      public LookupElement fun(XmlElementDescriptor descriptor) {
        LookupElementBuilder builder = LookupElementBuilder.create(descriptor.getName()).setInsertHandler(new XmlTagInsertHandler() {
          @Override
          protected boolean addTail(char completionChar,
                                    XmlElementDescriptor descriptor,
                                    XmlTag tag,
                                    Template template,
                                    boolean weInsertedSomeCodeThatCouldBeInvalidated,
                                    XmlAttributeDescriptor[] attributes,
                                    StringBuilder indirectRequiredAttrs) {

            if (descriptor.getContentType() != XmlElementDescriptor.CONTENT_TYPE_EMPTY) {
              template.addTextSegment(">");
              final MacroCallNode completeAttrExpr = new MacroCallNode(MacroFactory.createMacro("complete"));
              template.addVariable("contentComplete", completeAttrExpr, completeAttrExpr, true);
              weInsertedSomeCodeThatCouldBeInvalidated = true;
              template.addTextSegment("</" + tag.getName() + ">");
              template.addEndVariable();
            }
            return weInsertedSomeCodeThatCouldBeInvalidated;
          }
        });
        if (descriptor instanceof XmlElementDescriptorImpl) {
          builder = builder.setTailText(" (" + ((XmlElementDescriptorImpl)descriptor).getNamespace() + ")", true);
        }
        return builder;
      }
    }));
  }

  private static boolean processGroup(XmlElementsGroup group,
                                      List<XmlElementDescriptor> descriptors,
                                      Set<XmlElementsGroup> anchor,
                                      Set<XmlElementsGroup> existing, int maxOccurs) {
    maxOccurs *= group.getMaxOccurs();

    switch (group.getGroupType()) {
      case LEAF:
        if (maxOccurs > 1 || maxOccurs == 1 && !existing.contains(group))  {
          descriptors.add(group.getLeafDescriptor());
        }
        break;
      case SEQUENCE:
        if (anchor.isEmpty()) {
          if (!group.getSubGroups().isEmpty()) {
            return processGroup(group.getSubGroups().get(0), descriptors, anchor, existing, maxOccurs);
          }
        }
        else {
          for (Iterator<XmlElementsGroup> iterator = group.getSubGroups().iterator(); iterator.hasNext();) {
            XmlElementsGroup subGroup = iterator.next();
            if (anchor.contains(subGroup)) {
              processGroup(subGroup, descriptors, anchor, existing, maxOccurs);
              if (iterator.hasNext()) {
                return processGroup(iterator.next(), descriptors, anchor, existing, maxOccurs);
              }
              return true;
            }
          }
        }
        break;
      case CHOICE:
        if (group.getMaxOccurs() == 1) {
          for (XmlElementsGroup subGroup : group.getSubGroups()) {
            if (existing.contains(subGroup)) {
              return processGroup(subGroup, descriptors, anchor, existing, maxOccurs);
            }
          }
        }
      case ALL:
      case GROUP:
        for (XmlElementsGroup subGroup : group.getSubGroups()) {
          processGroup(subGroup, descriptors, anchor, existing, maxOccurs);
        }
        break;
    }
    return true;
  }

  public static List<XmlElementsGroup> getAllAncestors(XmlElementsGroup group) {
    List<XmlElementsGroup> list = new ArrayList<XmlElementsGroup>();
    while (group != null) {
      list.add(group);
      group = group.getParentGroup();
    }
    return list;
  }

}
