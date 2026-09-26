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

import com.intellij.util.text.VersionComparatorUtil;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.SingleResponseValueSource;
import org.codehaus.plexus.interpolation.ValueSource;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jetbrains.idea.maven.server.MavenServerEmbedder.MAVEN_EMBEDDER_VERSION;

@Component(role = ModelInterpolator.class, hint = "ide")
public class CustomMaven3ModelInterpolator2 extends StringSearchModelInterpolator {

  public static final String SHA1_PROPERTY = "sha1";
  public static final String CHANGELIST_PROPERTY = "changelist";
  public static final String REVISION_PROPERTY = "revision";

  private String localRepository;


  @Override
  public Model interpolateModel(Model model, File projectDir, ModelBuildingRequest config, ModelProblemCollector problems) {
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if (VersionComparatorUtil.compare(mavenVersion, "3.6.2") >= 0) {
      interpolateObjectFor362(model, model, projectDir, config, problems);
    }
    else {
      return super.interpolateModel(model, projectDir, config, problems);
    }
    return model;
  }

  @Override
  public void interpolateObject(Object obj, Model model, File projectDir, ModelBuildingRequest config, ModelProblemCollector problems) {
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if (VersionComparatorUtil.compare(mavenVersion, "3.6.2") >= 0) {
      interpolateObjectFor362(obj, model, projectDir, config, problems);
    }
    else {
      super.interpolateObject(obj, model, projectDir, config, problems);
    }
  }

  private void interpolateObjectFor362(Object obj,
                                       Model model,
                                       File projectDir,
                                       ModelBuildingRequest config,
                                       ModelProblemCollector problems) {
    try {
      Method interpolateObjectMethod = StringSearchModelInterpolator.class
        .getDeclaredMethod("interpolateObject", Object.class, Model.class, File.class, ModelBuildingRequest.class,
                           ModelProblemCollector.class);
      interpolateObjectMethod.setAccessible(true);
      interpolateObjectMethod.invoke(this, obj, model, projectDir, config, problems);
    }
    catch (Exception e) {
      problems.add(new ModelProblemCollectorRequest(ModelProblem.Severity.ERROR, ModelProblem.Version.BASE)
                     .setException(e)
                     .setMessage(e.getMessage()));
    }
  }

  @Override
  protected List<ValueSource> createValueSources(Model model,
                                                 File projectDir,
                                                 ModelBuildingRequest config,
                                                 ModelProblemCollector problems) {
    List<ValueSource> sources = super.createValueSources(model, projectDir, config, problems);

    if (localRepository != null) {
      sources.add(new SingleResponseValueSource("settings.localRepository", localRepository));
    }

    int firstMapIndex = -1;
    for (int i = 0; i < sources.size(); i++) {
      if (sources.get(i) instanceof MapBasedValueSource) {
        firstMapIndex = i;
        break;
      }
    }

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
    sources.add(firstMapIndex + 1, new MapBasedValueSource(rightOrderProperties));

    return sources;
  }

  public String getLocalRepository() {
    return localRepository;
  }

  public void setLocalRepository(String localRepository) {
    this.localRepository = localRepository;
  }
}
