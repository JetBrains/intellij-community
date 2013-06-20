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
package com.intellij.javaee;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NotNullLazyKey;

/**
 * @author mike
 */
@State(name = "ExternalResourceManagerImpl",
       storages = {@Storage( file = StoragePathMacros.APP_CONFIG + "/other.xml")})
public class ExternalResourceManagerImpl extends ExternalResourceManagerExImpl implements JDOMExternalizable {
  public ExternalResourceManagerImpl(PathMacrosImpl pathMacros) {
    super(pathMacros);
  }

  private static final NotNullLazyKey<ProjectResources, Project> INSTANCE_CACHE = ServiceManager.createLazyKey(ProjectResources.class);

  @Override
  protected ExternalResourceManagerExImpl getProjectResources(Project project) {
    return INSTANCE_CACHE.getValue(project);
  }
}
