/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet.completion;

import com.intellij.application.options.emmet.EmmetCompositeConfigurable;
import com.intellij.application.options.emmet.XmlEmmetConfigurable;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.codeInsight.template.emmet.ZenCodingTemplate;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;

public class EmmetLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(LookupElement element, final Lookup lookup, Consumer<LookupElementAction> consumer) {
    if (element instanceof CustomLiveTemplateLookupElement  && 
        ((CustomLiveTemplateLookupElement)element).getCustomLiveTemplate() instanceof ZenCodingTemplate) {

      final PsiElement context = lookup.getPsiElement();
      final ZenCodingGenerator generator = context != null ? ZenCodingTemplate.findApplicableDefaultGenerator(context, false) : null;
      if (generator != null) {
        consumer.consume(new LookupElementAction(PlatformIcons.EDIT, "Edit Emmet settings") {
          @Override
          public Result performLookupAction() {
            final Project project = lookup.getEditor().getProject();
            assert project != null;
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (project.isDisposed()) return;

                final Configurable generatorSpecificConfigurable = generator.createConfigurable();
                EmmetCompositeConfigurable configurable = generatorSpecificConfigurable != null
                                                          ? new EmmetCompositeConfigurable(generatorSpecificConfigurable)
                                                          : new EmmetCompositeConfigurable(new XmlEmmetConfigurable());
                ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
              }
            });
            return Result.HIDE_LOOKUP;
          }
        });

        consumer.consume(new LookupElementAction(AllIcons.Actions.Delete, "Disable Emmet") {
          @Override
          public Result performLookupAction() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                generator.disableEmmet();
              }
            });
            return Result.HIDE_LOOKUP;
          }
        });
      }
    }
  }
}
