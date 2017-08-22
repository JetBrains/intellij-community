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
package org.jetbrains.idea.maven.server.embedder;

import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.apache.maven.model.validation.ModelValidator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import static com.intellij.openapi.util.text.StringUtil.notNullize;

/**
 * @author ibessonov
 */
@Component(role = ModelValidator.class, hint = "ide")
public class CustomModelValidator extends DefaultModelValidator {

  @Requirement(role = ModelInterpolator.class)
  private CustomMaven3ModelInterpolator2 myModelInterpolator;

  @Override
  public void validateRawModel(Model model, ModelBuildingRequest request, ModelProblemCollector problems) {
    ProxyModelProblemCollector problemsProxy = new ProxyModelProblemCollector(problems);
    super.validateRawModel(model, request, problemsProxy);

    Parent parent = model.getParent();
    if (parent != null && !problemsProxy.hasFatalErrors()) {
      if (parent.getGroupId().contains("${") || parent.getArtifactId().contains("${") || parent.getVersion().contains("${")) {
        myModelInterpolator.interpolateObject(parent, model, model.getProjectDirectory(), request, problems);

        if (equals(parent.getGroupId(), model.getGroupId()) && equals(parent.getArtifactId(), model.getArtifactId())) {
          try {
            // will encounter validation problem right on the start
            super.validateRawModel(model, request, new AbortOnFirstErrorProblemsCollector(problems));
          }
          catch (AbortOnErrorException ignored) {
          }
        }
      }
    }
  }

  private static boolean equals(String a, String b) {
    return notNullize(a).equals(notNullize(b));
  }

  private static class ProxyModelProblemCollector implements ModelProblemCollector {

    private final ModelProblemCollector myDelegate;
    private boolean myHasFatalErrors = false;

    public ProxyModelProblemCollector(ModelProblemCollector delegate) {
      myDelegate = delegate;
    }

    @Override
    public void add(ModelProblem.Severity severity, String s, InputLocation location, Exception e) {
      myHasFatalErrors |= (severity == ModelProblem.Severity.FATAL);
      myDelegate.add(severity, s, location, e);
    }

    public boolean hasFatalErrors() {
      return myHasFatalErrors;
    }
  }

  private static class AbortOnErrorException extends RuntimeException {
  }

  private static class AbortOnFirstErrorProblemsCollector extends ProxyModelProblemCollector {

    public AbortOnFirstErrorProblemsCollector(ModelProblemCollector delegate) {
      super(delegate);
    }

    @Override
    public void add(ModelProblem.Severity severity, String s, InputLocation location, Exception e) {
      super.add(severity, s, location, e);
      throw new AbortOnErrorException();
    }
  }
}
