// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.emmet.completion;

import com.intellij.application.options.emmet.EmmetCompositeConfigurable;
import com.intellij.application.options.emmet.XmlEmmetConfigurable;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.ZenCodingTemplate;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

public class EmmetLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(@NotNull LookupElement element, final @NotNull Lookup lookup, @NotNull Consumer<@NotNull LookupElementAction> consumer) {
    if (element instanceof CustomLiveTemplateLookupElement  && 
        ((CustomLiveTemplateLookupElement)element).getCustomLiveTemplate() instanceof ZenCodingTemplate) {

      PsiFile file = lookup.getPsiFile();
      if (file == null) {
        return;
      }
      CustomTemplateCallback callback = new CustomTemplateCallback(lookup.getEditor(), file);
      final ZenCodingGenerator generator = ZenCodingTemplate.findApplicableDefaultGenerator(callback, false);
      if (generator != null) {
        consumer.consume(new LookupElementAction(PlatformIcons.EDIT, XmlBundle.message("edit.emmet.settings")) {
          @Override
          public Result performLookupAction() {
            final Project project = lookup.getProject();
            ApplicationManager.getApplication().invokeLater(() -> {
              if (project.isDisposed()) return;

              final Configurable generatorSpecificConfigurable = generator.createConfigurable();
              Configurable configurable = generatorSpecificConfigurable != null ? generatorSpecificConfigurable : new XmlEmmetConfigurable();
              ShowSettingsUtil.getInstance().editConfigurable(project, new EmmetCompositeConfigurable(configurable));
            });
            return Result.HIDE_LOOKUP;
          }
        });

        consumer.consume(new LookupElementAction(AllIcons.Actions.Cancel, XmlBundle.message("disable.emmet")) {
          @Override
          public Result performLookupAction() {
            ApplicationManager.getApplication().invokeLater(generator::disableEmmet);
            return Result.HIDE_LOOKUP;
          }
        });
      }
    }
  }
}
