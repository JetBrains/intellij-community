/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.codeInsight.completion.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PropertiesCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.isExtendedCompletion()) {
      CompletionService.getCompletionService().getVariantsFromContributors(parameters.delegateToClassName(), null, new Consumer<CompletionResult>() {
        public void consume(final CompletionResult completionResult) {
          result.passResult(completionResult);
        }
      });
    }
  }

  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    if (context.getFile() instanceof PropertiesFile) {
      context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);
    }
  }
}
