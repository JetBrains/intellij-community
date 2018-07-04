// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "MavenCustomPomFilePath")
public class MavenPomPathModuleService implements PersistentStateComponent<MavenPomPathModuleService.MavenPomPathState> {

  private final MavenPomPathState myState = new MavenPomPathState();

  public static MavenPomPathModuleService getInstance(final Module module) {
    return ModuleServiceManager.getService(module, MavenPomPathModuleService.class);
  }

  public String getPomFileUrl() {
    return myState.mavenPomFileUrl;
  }

  public void setPomFileUrl(String pomFileUrl) {
    myState.mavenPomFileUrl = pomFileUrl;
  }

  @Nullable
  @Override
  public MavenPomPathState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull MavenPomPathState state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  public static class MavenPomPathState {

    @Property(filter = SkipDefaultsSerializationFilter.class)
    @Nullable
    public String mavenPomFileUrl;
  }
}
