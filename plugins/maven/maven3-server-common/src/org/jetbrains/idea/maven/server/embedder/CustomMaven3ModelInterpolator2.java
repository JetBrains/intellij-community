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
package org.jetbrains.idea.maven.server.embedder;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.interpolation.SingleResponseValueSource;
import org.codehaus.plexus.interpolation.ValueSource;

import java.io.File;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
@Component( role = ModelInterpolator.class, hint = "ide")
public class CustomMaven3ModelInterpolator2 extends StringSearchModelInterpolator {

  private String localRepository;

  @Override
  protected List<ValueSource> createValueSources(Model model,
                                                 File projectDir,
                                                 ModelBuildingRequest config,
                                                 ModelProblemCollector problems) {
    List<ValueSource> res = super.createValueSources(model, projectDir, config, problems);

    if (localRepository != null) {
      res.add(new SingleResponseValueSource("settings.localRepository", localRepository));
    }

    return res;
  }

  public String getLocalRepository() {
    return localRepository;
  }

  public void setLocalRepository(String localRepository) {
    this.localRepository = localRepository;
  }
}
