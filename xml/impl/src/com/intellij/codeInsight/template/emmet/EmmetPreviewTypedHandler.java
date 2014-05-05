/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretAdapter;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EmmetPreviewTypedHandler extends TypedHandlerDelegate {
  @Override
  public Result charTyped(char c, @NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (EmmetOptions.getInstance().isPreviewEnabled()) {
      EmmetPreviewHint existingBalloon = EmmetPreviewHint.getExistingHint(editor);
      if (existingBalloon == null) {
        String templateText = calculateTemplateText(editor, file);
        if (!StringUtil.isEmpty(templateText)) {
          EmmetPreviewHint previewBalloon = EmmetPreviewHint.createHint(editor, templateText, file.getFileType());
          previewBalloon.showHint();
          
          editor.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void documentChanged(@NotNull DocumentEvent e) {
              EmmetPreviewHint existingHint = EmmetPreviewHint.getExistingHint(editor);
              if (existingHint != null) {
                existingHint.updateText(calculateTemplateText(editor, file));
              }
              else {
                e.getDocument().removeDocumentListener(this);
              }
            }
          });
  
          editor.getCaretModel().addCaretListener(new CaretAdapter() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent e) {
              EmmetPreviewHint existingHint = EmmetPreviewHint.getExistingHint(e.getEditor());
              if (existingHint != null) {
                existingHint.updateText(calculateTemplateText(editor, file));
              }
              else {
                e.getEditor().getCaretModel().removeCaretListener(this);
              }
            }
          });
        }
      }
    }

    return super.charTyped(c, project, editor, file);
  }

  @Nullable
  private static String calculateTemplateText(@NotNull Editor editor, @NotNull PsiFile file) {
    if (file instanceof XmlFile) {
      int offset = editor.getCaretModel().getOffset();
      final Ref<TemplateImpl> generatedTemplate = new Ref<TemplateImpl>();
      CustomTemplateCallback callback = createCallback(editor, file, generatedTemplate);
      PsiElement context = callback.getContext();
      ZenCodingGenerator generator = ZenCodingTemplate.findApplicableDefaultGenerator(context, false);
      if (generator != null) {
        final String templatePrefix = new ZenCodingTemplate().computeTemplateKeyWithoutContextChecking(callback);
        if (templatePrefix != null) {
          List<TemplateImpl> regularTemplates = TemplateManagerImpl.listApplicableTemplates(file, offset, false);
          boolean regularTemplateWithSamePrefixExists = !ContainerUtil.filter(regularTemplates, new Condition<TemplateImpl>() {
            @Override
            public boolean value(@NotNull TemplateImpl template) {
              return templatePrefix.equals(template.getKey());
            }
          }).isEmpty();

          if (!regularTemplateWithSamePrefixExists) {
            // exclude perfect matches with existing templates because LiveTemplateCompletionContributor handles it
            ZenCodingTemplate.expand(templatePrefix, callback, null, generator, Collections.<ZenCodingFilter>emptyList(), false, 0);
            TemplateImpl template = generatedTemplate.get();
            String templateText = template != null ? template.getTemplateText() : null;
            if (!StringUtil.isEmpty(templateText)) {
              return reformatTemplateText(file, templateText);
            }
          }
        }
      }
    }
    return null;
  }

  private static String reformatTemplateText(@NotNull PsiFile file, @NotNull String templateText) {
    PsiFile copy = PsiFileFactory.getInstance(file.getProject()).createFileFromText(file.getName(), file.getFileType(), templateText);
    VirtualFile vFile = copy.getVirtualFile();
    if (vFile != null) {
      vFile.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    }
    CodeStyleManager.getInstance(file.getProject()).reformat(copy);
    return copy.getText();
  }

  @NotNull
  private static CustomTemplateCallback createCallback(@NotNull Editor editor,
                                                       @NotNull PsiFile file,
                                                       @NotNull final Ref<TemplateImpl> generatedTemplate) {
    return new CustomTemplateCallback(editor, file, false) {
      @Override
      public void startTemplate(@NotNull Template template, Map<String, String> predefinedValues, TemplateEditingListener listener) {
        if (template instanceof TemplateImpl && !((TemplateImpl)template).isDeactivated()) {
          generatedTemplate.set((TemplateImpl)template);
        }
      }

      @Override
      public void deleteTemplateKey(@NotNull String key) {
      }
    };
  }
}
