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
package org.jetbrains.idea.maven.server.embedder;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.apache.maven.model.Model;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.StringSearchModelInterpolator;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.ValueSource;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator2.*;

public class CustomMaven3ModelInterpolator extends StringSearchModelInterpolator {
  public CustomMaven3ModelInterpolator() {
  }

  public CustomMaven3ModelInterpolator(DefaultPathTranslator pathTranslator) {
    super(pathTranslator);
  }

  @Override
  public Model interpolate(Model model, File projectDir, ProjectBuilderConfiguration config, boolean debugEnabled)
      throws ModelInterpolationException {
    this.interpolateObject(ContainerUtil.ar(model.getParent(), model), model, projectDir, config, debugEnabled);
    return model;
  }

  @Override
  protected List<ValueSource> createValueSources(Model model, File projectDir, ProjectBuilderConfiguration config) {
    List<ValueSource> sources = super.createValueSources(model, projectDir, config);

    int firstMapIndex = ContainerUtil.indexOf(sources, new Condition<ValueSource>() {
      @Override
      public boolean value(ValueSource source) {
        return source instanceof MapBasedValueSource;
      }
    });

    Map<String, Object> rightOrderProperties = new HashMap<String, Object>(3);
    if (config.getExecutionProperties().containsKey(REVISION_PROPERTY)) {
      rightOrderProperties.put(REVISION_PROPERTY, config.getExecutionProperties().getProperty(REVISION_PROPERTY));
    }
    if (config.getExecutionProperties().containsKey(CHANGELIST_PROPERTY)) {
      rightOrderProperties.put(CHANGELIST_PROPERTY, config.getExecutionProperties().getProperty(CHANGELIST_PROPERTY));
    }
    if (config.getExecutionProperties().containsKey(SHA1_PROPERTY)) {
      rightOrderProperties.put(SHA1_PROPERTY, config.getExecutionProperties().getProperty(SHA1_PROPERTY));
    }
    // these 3 system properties must be resolved before model properties
    sources.add(firstMapIndex + 1, new MapBasedValueSource(rightOrderProperties));

    return sources;
  }

  @Override
  protected void interpolateObject(Object obj,
                                                Model model,
                                                File projectDir,
                                                ProjectBuilderConfiguration config,
                                                boolean debugEnabled) throws ModelInterpolationException {
    if (obj == null) return;

    // IDEA-74131 avoid concurrent access to the static cache in StringSearchModelInterpolator
    synchronized (CustomMaven3ModelInterpolator.class) {
      try {
        super.interpolateObject(obj, model, projectDir, config, debugEnabled);
      }
      catch (NullPointerException e) {
        // npe may be thrown from here:
        //at org.apache.maven.project.interpolation.StringSearchModelInterpolator$InterpolateObjectAction.isQualifiedForInterpolation(StringSearchModelInterpolator.java:344)
        //at org.apache.maven.project.interpolation.StringSearchModelInterpolator$InterpolateObjectAction.traverseObjectWithParents(StringSearchModelInterpolator.java:172)
        //at org.apache.maven.project.interpolation.StringSearchModelInterpolator$InterpolateObjectAction.traverseObjectWithParents(StringSearchModelInterpolator.java:328)
        //at org.apache.maven.project.interpolation.StringSearchModelInterpolator$InterpolateObjectAction.run(StringSearchModelInterpolator.java:135)
        //at org.apache.maven.project.interpolation.StringSearchModelInterpolator$InterpolateObjectAction.run(StringSearchModelInterpolator.java:102)
        throw new ModelInterpolationException("Cannot interpolate", e);
      }
    }
  }
}
