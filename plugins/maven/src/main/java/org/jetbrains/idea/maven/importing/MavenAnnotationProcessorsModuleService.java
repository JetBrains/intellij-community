// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.maven.model.impl.MavenAnnotationProcessorsModel;

import java.util.List;

/**
 * @author ibessonov
 */
@State(name = MavenAnnotationProcessorsModel.COMPONENT_NAME)
public class MavenAnnotationProcessorsModuleService implements PersistentStateComponent<MavenAnnotationProcessorsModel> {

  private final MavenAnnotationProcessorsModel myState = new MavenAnnotationProcessorsModel();

  public static MavenAnnotationProcessorsModuleService getInstance(Module module) {
    return ModuleServiceManager.getService(module, MavenAnnotationProcessorsModuleService.class);
  }

  public List<String> getAnnotationProcessorModules() {
    return myState.annotationProcessorModules;
  }

  public void setAnnotationProcessorModules(List<String> annotationProcessorModules) {
    myState.annotationProcessorModules = annotationProcessorModules;
  }

  @Nullable
  @Override
  public MavenAnnotationProcessorsModel getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull MavenAnnotationProcessorsModel state) {
    XmlSerializerUtil.copyBean(state, myState);
  }
}
