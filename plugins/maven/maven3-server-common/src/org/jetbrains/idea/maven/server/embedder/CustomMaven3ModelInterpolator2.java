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

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.SingleResponseValueSource;
import org.codehaus.plexus.interpolation.ValueSource;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
@Component( role = ModelInterpolator.class, hint = "ide")
public class CustomMaven3ModelInterpolator2 extends StringSearchModelInterpolator {

  public static final String SHA1_PROPERTY       = "sha1";
  public static final String CHANGELIST_PROPERTY = "changelist";
  public static final String REVISION_PROPERTY   = "revision";

  private String localRepository;

  @Override
  public void interpolateObject(Object obj, Model model, File projectDir, ModelBuildingRequest config, ModelProblemCollector problems) {
    super.interpolateObject(obj, model, projectDir, config, problems);
  }

  @Override
  protected List<ValueSource> createValueSources(Model model,
                                                 File projectDir,
                                                 ModelBuildingRequest config,
                                                 ModelProblemCollector problems) {
    List<ValueSource> res = super.createValueSources(model, projectDir, config, problems);

    if (localRepository != null) {
      res.add(new SingleResponseValueSource("settings.localRepository", localRepository));
    }

    int firstMapIndex = ContainerUtil.indexOf(res, new Condition<ValueSource>() {
      @Override
      public boolean value(ValueSource source) {
        return source instanceof MapBasedValueSource;
      }
    });

    Map<String, Object> rightOrderProperties = new HashMap<String, Object>(3);
    if (config.getSystemProperties().containsKey(REVISION_PROPERTY)) {
      rightOrderProperties.put(REVISION_PROPERTY, config.getSystemProperties().getProperty(REVISION_PROPERTY));
    }
    if (config.getSystemProperties().containsKey(CHANGELIST_PROPERTY)) {
      rightOrderProperties.put(CHANGELIST_PROPERTY, config.getSystemProperties().getProperty(CHANGELIST_PROPERTY));
    }
    if (config.getSystemProperties().containsKey(SHA1_PROPERTY)) {
      rightOrderProperties.put(SHA1_PROPERTY, config.getSystemProperties().getProperty(SHA1_PROPERTY));
    }
    // these 3 system properties must be resolved before model properties
    res.add(firstMapIndex + 1, new MapBasedValueSource(rightOrderProperties));

    return res;
  }

  public String getLocalRepository() {
    return localRepository;
  }

  public void setLocalRepository(String localRepository) {
    this.localRepository = localRepository;
  }
}
