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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.actions.SimpleCodeInsightAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInsight.template.macro.CompleteSmartMacro;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.impl.schema.ComplexTypeDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class GenerateXmlTagAction extends SimpleCodeInsightAction {

  public static final ThreadLocal<String> TEST_THREAD_LOCAL = new ThreadLocal<String>();

  @Override
  public void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull final PsiFile file) {
    try {
      final XmlTag contextTag = getContextTag(editor, file);
      if (contextTag == null) {
        throw new CommonRefactoringUtil.RefactoringErrorHintException("Caret should be positioned inside a tag");
      }
      XmlElementDescriptor currentTagDescriptor = contextTag.getDescriptor();
      final XmlElementDescriptor[] descriptors = currentTagDescriptor.getElementsDescriptors(contextTag);
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
          append(" " + getNamespace(descriptor), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      });
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          final XmlElementDescriptor selected = (XmlElementDescriptor)list.getSelectedValue();
          new WriteCommandAction.Simple(project, "Generate XML Tag", file) {
            @Override
            protected void run() {
              XmlTag newTag = createTag(contextTag, selected);
              newTag = contextTag.addSubTag(newTag, false);
              generateTag(newTag);
            }
          }.execute();
        }
      };
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        XmlElementDescriptor descriptor = ContainerUtil.find(descriptors, new Condition<XmlElementDescriptor>() {
          @Override
          public boolean value(XmlElementDescriptor xmlElementDescriptor) {
            return xmlElementDescriptor.getName().equals(TEST_THREAD_LOCAL.get());
          }
        });
        list.setSelectedValue(descriptor, false);
        runnable.run();
      }
      else {
        JBPopupFactory.getInstance().createListPopupBuilder(list).setTitle("Choose Tag Name").setItemChoosenCallback(runnable).createPopup().showInBestPositionFor(editor);
      }
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      HintManager.getInstance().showErrorHint(editor, e.getMessage());
    }
  }

  public static void generateTag(XmlTag newTag) {
    newTag = generateRaw(newTag);
    newTag = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newTag);
    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(newTag);
    replaceElements(newTag, builder);
    builder.run();
  }

  private static XmlTag generateRaw(XmlTag newTag) {
    XmlElementDescriptor selected = newTag.getDescriptor();
    if (selected == null) return newTag;
    XmlElementsGroup topGroup = selected.getTopGroup();
    if (topGroup == null) return newTag;
    List<XmlElementDescriptor> tags = new ArrayList<XmlElementDescriptor>();
    computeRequiredSubTags(topGroup, tags);
    for (XmlElementDescriptor descriptor : tags) {
      if (descriptor == null) {
        XmlTag tag = XmlElementFactory.getInstance(newTag.getProject()).createTagFromText("<", newTag.getLanguage());
        newTag.addSubTag(tag, false);
      }
      else {
        XmlTag subTag = newTag.addSubTag(createTag(newTag, descriptor), false);
        generateRaw(subTag);
      }
    }
    return newTag;
  }

  private static void replaceElements(XmlTag newTag, TemplateBuilder builder) {
    for (XmlTag tag : newTag.getSubTags()) {
      if ("<".equals(tag.getText())) {
        builder.replaceElement(tag, TextRange.from(1, 0), new MacroCallNode(new CompleteSmartMacro()));
      }
      else if (tag.getSubTags().length > 0) {
        replaceElements(tag, builder);
      }
      else {
        for (XmlAttribute attribute : tag.getAttributes()) {
          builder.replaceElement(attribute.getValueElement(), new MacroCallNode(new CompleteMacro()));
        }
        int i = tag.getText().indexOf("></");
        if (i > 0) {
          builder.replaceElement(tag, TextRange.from(i + 1, 0), new MacroCallNode(new CompleteMacro()));
        }
      }
    }
  }

  private static XmlTag createTag(XmlTag contextTag, XmlElementDescriptor descriptor) {
    String bodyText = null;
    if (descriptor instanceof XmlElementDescriptorImpl) {
      TypeDescriptor type = ((XmlElementDescriptorImpl)descriptor).getType();
      if (type instanceof ComplexTypeDescriptor) {
        int contentType = ((ComplexTypeDescriptor)type).getContentType();
        bodyText = contentType == XmlElementDescriptor.CONTENT_TYPE_EMPTY ? null : "";
      }
    }
    String namespace = getNamespace(descriptor);
    return contextTag.createChildTag(descriptor.getName(), namespace, bodyText, false);
  }

  private static String getNamespace(XmlElementDescriptor descriptor) {
    return descriptor instanceof XmlElementDescriptorImpl ? ((XmlElementDescriptorImpl)descriptor).getNamespace() : "";
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

  private static void computeRequiredSubTags(XmlElementsGroup group, List<XmlElementDescriptor> tags) {

    if (group.getMinOccurs() < 1) return;
    switch (group.getGroupType()) {
      case LEAF:
        tags.add(group.getLeafDescriptor());
        return;
      case CHOICE:
        tags.add(null); // placeholder for smart completion
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
