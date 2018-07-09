/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.EmmetAbbreviationBalloon.EmmetContextHelp;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGeneratorImpl;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PopupAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PairProcessor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class EmmetUpdateTagAction extends BaseCodeInsightAction implements DumbAware, PopupAction {
  private static final String EMMET_RECENT_UPDATE_ABBREVIATIONS_KEY = "emmet.recent.update.abbreviations";
  private static final String EMMET_LAST_UPDATE_ABBREVIATIONS_KEY = "emmet.last.update.abbreviations";
  private static final String DOCUMENTATION = "Update HTML tag with Emmet abbreviation:<br/>" +
                                              ".class[attribute] to overwrite value;<br/>" +
                                              ".+class[attribute] to append value;<br/>" +
                                              ".-class[attribute] to remove value.<br/>" +
                                              "<p/>" +
                                              "For example, <code>.+c2[title=Hello]</code> abbreviation updates<br/>" +
                                              "<code>&lt;div class=\"c1\"&gt;</code> to<br/>" +
                                              "<code>&lt;div class=\"c1 c2\" title=\"Hello\"&gt;</code>.";
  private static final EmmetContextHelp CONTEXT_HELP = new EmmetContextHelp(DOCUMENTATION);

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
        final XmlTag tag = findTag(editor, file);
        if (tag != null) {
          new EmmetAbbreviationBalloon(EMMET_RECENT_UPDATE_ABBREVIATIONS_KEY, EMMET_LAST_UPDATE_ABBREVIATIONS_KEY,
                                       new EmmetAbbreviationBalloon.Callback() {
                                         @Override
                                         public void onEnter(@NotNull String abbreviation) {
                                           try {
                                             doUpdateTag(abbreviation, tag, file, editor);
                                           }
                                           catch (EmmetException ignore) {
                                           }
                                         }
                                       }, CONTEXT_HELP).show(new CustomTemplateCallback(editor, file));
        }
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }

  public void doUpdateTag(@NotNull final String abbreviation,
                          @NotNull final XmlTag tag,
                          @NotNull PsiFile file,
                          @NotNull Editor editor) throws EmmetException {
    if (tag.isValid()) {
      String templateText = expandTemplate(abbreviation, file, editor);

      final Collection<String> classNames = ContainerUtil.newLinkedHashSet();
      ContainerUtil.addAll(classNames, HtmlUtil.splitClassNames(tag.getAttributeValue(HtmlUtil.CLASS_ATTRIBUTE_NAME)));
      final Map<String, String> attributes = ContainerUtil.newLinkedHashMap();
      final Ref<String> newTagName = Ref.create();
      processTags(file.getProject(), templateText, (tag1, firstTag) -> {
        if (firstTag && !abbreviation.isEmpty() && StringUtil.isJavaIdentifierPart(abbreviation.charAt(0))) {
          newTagName.set(tag1.getName());
        }

        for (String clazz : HtmlUtil.splitClassNames(tag1.getAttributeValue(HtmlUtil.CLASS_ATTRIBUTE_NAME))) {
          if (StringUtil.startsWithChar(clazz, '+')) {
            classNames.add(clazz.substring(1));
          }
          else if (StringUtil.startsWithChar(clazz, '-')) {
            classNames.remove(clazz.substring(1));
          }
          else {
            classNames.clear();
            classNames.add(clazz);
          }
        }

        if (!firstTag) {
          classNames.add(tag1.getName());
        }

        for (XmlAttribute xmlAttribute : tag1.getAttributes()) {
          if (!HtmlUtil.CLASS_ATTRIBUTE_NAME.equalsIgnoreCase(xmlAttribute.getName())) {
            attributes.put(xmlAttribute.getName(), StringUtil.notNullize(xmlAttribute.getValue()));
          }
        }
        return true;
      });

      WriteCommandAction.writeCommandAction(file.getProject(), file).run(doUpdateTagAttributes(tag, file, newTagName.get(), classNames, attributes));
    }
  }

  @Nullable
  private static String expandTemplate(@NotNull String abbreviation, @NotNull PsiFile file, @NotNull Editor editor) throws EmmetException {
    final CollectCustomTemplateCallback callback = new CollectCustomTemplateCallback(editor, file);
    ZenCodingTemplate.expand(abbreviation, callback, XmlZenCodingGeneratorImpl.INSTANCE, Collections.emptyList(),
                             true, Registry.intValue("emmet.segments.limit"));
    TemplateImpl template = callback.getGeneratedTemplate();
    return template != null ? template.getTemplateText() : null;
  }

  private static void processTags(@NotNull Project project,
                                  @Nullable String templateText,
                                  @NotNull PairProcessor<XmlTag, Boolean> processor) {
    if (StringUtil.isNotEmpty(templateText)) {
      final PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(project);
      XmlFile xmlFile = (XmlFile)psiFileFactory.createFileFromText("dummy.xml", StdFileTypes.HTML, templateText);
      XmlTag tag = xmlFile.getRootTag();
      boolean firstTag = true;

      while (tag != null) {
        processor.process(tag, firstTag);
        firstTag = false;
        tag = PsiTreeUtil.getNextSiblingOfType(tag, XmlTag.class);
      }
    }
  }

  @NotNull
  private static ThrowableRunnable<RuntimeException> doUpdateTagAttributes(@NotNull final XmlTag tag,
                                                         @NotNull final PsiFile file,
                                                         @Nullable final String newTagName,
                                                         @NotNull final Collection<String> classes,
                                                         @NotNull final Map<String, String> attributes) {
    return ()->{
      if (tag.isValid()) {
        if (!ReadonlyStatusHandler.getInstance(file.getProject()).ensureFilesWritable(file.getVirtualFile()).hasReadonlyFiles()) {
          tag.setAttribute(HtmlUtil.CLASS_ATTRIBUTE_NAME, StringUtil.join(classes, " ").trim());

          for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            final String attributeName = attribute.getKey();
            if (StringUtil.startsWithChar(attributeName, '+')) {
              final XmlAttribute existingAttribute = tag.getAttribute(attributeName.substring(1));
              if (existingAttribute != null) {
                existingAttribute.setValue(StringUtil.notNullize(existingAttribute.getValue() + attribute.getValue()));
              }
              else {
                tag.setAttribute(attributeName.substring(1), attribute.getValue());
              }
            }
            else if (StringUtil.startsWithChar(attributeName, '-')) {
              final XmlAttribute existingAttribute = tag.getAttribute(attributeName.substring(1));
              if (existingAttribute != null) {
                existingAttribute.delete();
              }
            }
            else {
              tag.setAttribute(attributeName, attribute.getValue());
            }
          }

          if (newTagName != null) {
            tag.setName(newTagName);
          }
        }
      }
    };
  }


  @Override
  public void update(AnActionEvent event) {
    super.update(event);
    event.getPresentation().setVisible(event.getPresentation().isEnabled());
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return super.isValidForFile(project, editor, file) && EmmetOptions.getInstance().isEmmetEnabled() && findTag(editor, file) != null;
  }

  @Nullable
  private static XmlTag findTag(@NotNull Editor editor, @NotNull PsiFile file) {
    final XmlTag tag = PsiTreeUtil.getNonStrictParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), XmlTag.class);
    return tag != null && HtmlUtil.isHtmlTag(tag) ? tag : null;
  }
}
