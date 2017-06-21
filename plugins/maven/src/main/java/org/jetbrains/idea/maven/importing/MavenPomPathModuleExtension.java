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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MavenPomPathModuleExtension extends ModuleExtension implements PersistentStateComponent<MavenPomPathModuleExtension.MavenPomPathState> {

  private MavenPomPathState myState = new MavenPomPathState();
  private boolean myPomFileUrlChanged = false;

  public static MavenPomPathModuleExtension getInstance(final Module module) {
    return ModuleRootManager.getInstance(module).getModuleExtension(MavenPomPathModuleExtension.class);
  }

  public String getPomFileUrl() {
    return myState.mavenPomFileUrl;
  }

  public void setPomFileUrl(String pomFileUrl) {
    if (!FileUtil.pathsEqual(myState.mavenPomFileUrl, pomFileUrl)) {
      myState.mavenPomFileUrl = pomFileUrl;
      myPomFileUrlChanged = true;
    }
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

  @Override
  public ModuleExtension getModifiableModel(boolean writable) {
    return new MavenPomPathModuleExtension();
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean isChanged() {
    return myPomFileUrlChanged;
  }

  @Override
  public void dispose() {
  }

  @Override
  public void readExternal(@NotNull Element element) {
  }

  @Override
  public void writeExternal(@NotNull Element element) {
  }

  public static class MavenPomPathState {

    @Property(filter = SkipDefaultsSerializationFilter.class)
    @Nullable
    public String mavenPomFileUrl;
  }
}
