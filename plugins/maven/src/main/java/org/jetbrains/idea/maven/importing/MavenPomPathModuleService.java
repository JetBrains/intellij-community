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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.Nullable;

@State(name = "MavenCustomPomFilePath")
public class MavenPomPathModuleService implements PersistentStateComponent<MavenPomPathModuleService.MavenPomPathState> {

  private MavenPomPathState myState = new MavenPomPathState();

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
  public void loadState(MavenPomPathState state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  public static class MavenPomPathState {

    @Property(filter = SkipDefaultsSerializationFilter.class)
    @Nullable
    public String mavenPomFileUrl;
  }
}
