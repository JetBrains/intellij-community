/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.facade.embedder;

import org.apache.maven.model.Model;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.StringSearchModelInterpolator;
import org.apache.maven.project.path.DefaultPathTranslator;

import java.io.File;

public class CustomModelInterpolator extends StringSearchModelInterpolator {
  public CustomModelInterpolator() {
  }

  public CustomModelInterpolator(DefaultPathTranslator pathTranslator) {
    super(pathTranslator);
  }

  @Override
  protected synchronized void interpolateObject(Object obj,
                                                Model model,
                                                File projectDir,
                                                ProjectBuilderConfiguration config,
                                                boolean debugEnabled) throws ModelInterpolationException {
    super.interpolateObject(obj, model, projectDir, config, debugEnabled);
  }
}
