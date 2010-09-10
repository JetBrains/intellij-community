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
package com.intellij.xml.actions;

import com.intellij.codeInsight.actions.SimpleCodeInsightAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class GenerateXmlTagAction extends SimpleCodeInsightAction {

  @Override
  public void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull final PsiFile file) {
    try {
      final XmlTag contextTag = getContextTag(editor, file);
      if (contextTag == null) {
        throw new CommonRefactoringUtil.RefactoringErrorHintException("Caret should be positioned inside a tag");
      }
      XmlElementDescriptor currentTagDescriptor = contextTag.getDescriptor();
      XmlElementDescriptor[] descriptors = currentTagDescriptor.getElementsDescriptors(contextTag);
      Arrays.sort(descriptors, new Comparator<XmlElementDescriptor>() {
        @Override
        public int compare(XmlElementDescriptor o1, XmlElementDescriptor o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });
      final JBList list = new JBList(descriptors);
      list.setCellRenderer(new ColoredListCellRenderer() {
        @Override
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          XmlElementDescriptor descriptor = (XmlElementDescriptor)value;
          append(descriptor.getName());
          XmlNSDescriptor nsDescriptor = descriptor.getNSDescriptor();
          if (nsDescriptor instanceof XmlNSDescriptorImpl) {
            append(" " + ((XmlNSDescriptorImpl)nsDescriptor).getDefaultNamespace(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      });
      JBPopupFactory.getInstance().createListPopupBuilder(list).setTitle("Choose Tag Name").setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          final XmlElementDescriptor selected = (XmlElementDescriptor)list.getSelectedValue();
          final XmlElementsGroup topGroup = selected.getTopGroup();
          if (topGroup == null) {
            throw new CommonRefactoringUtil.RefactoringErrorHintException("XML Schema does not provide enough information to generate tags");
          }
          LinkedHashMap<String, XmlElementDescriptor> requiredSubTags = new LinkedHashMap<String, XmlElementDescriptor>();
          computeRequiredSubTags(topGroup, requiredSubTags);
          final Collection<XmlElementDescriptor> values = requiredSubTags.values();
          new WriteCommandAction.Simple(project, "Generate XML Tag", file) {
            @Override
            protected void run() {
              String namespace = selected instanceof XmlNSDescriptorImpl ? ((XmlNSDescriptorImpl)selected).getDefaultNamespace() : "";
              XmlTag newTag = contextTag.createChildTag(selected.getQualifiedName(), null, "", true);
              XmlTag tag = contextTag.addSubTag(newTag, false);
              for (XmlElementDescriptor descriptor : values) {

              }
            }
          }.execute();

        }
      }).createPopup().showInBestPositionFor(editor);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      HintManager.getInstance().showErrorHint(editor, e.getMessage());
    }
  }

  @Nullable
  private static XmlTag getContextTag(Editor editor, PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    XmlTag tag = null;
    if (element != null) {
      tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    }
    if (tag == null) {
      tag = ((XmlFile)file).getRootTag();
    }
    return tag;
  }

  private static void computeRequiredSubTags(XmlElementsGroup group, LinkedHashMap<String, XmlElementDescriptor> tags) {

    if (group.getMinOccurs() < 1) return;
    switch (group.getGroupType()) {
      case LEAF:
        XmlElementDescriptor descriptor = group.getLeafDescriptor();
        tags.put(descriptor.getName(), descriptor);
        return;
      case CHOICE:
        return;
      default:
        for (XmlElementsGroup subGroup : group.getSubGroups()) {
          computeRequiredSubTags(subGroup, tags);
        }
    }
  }

  @Override
  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    return file instanceof XmlFile;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
