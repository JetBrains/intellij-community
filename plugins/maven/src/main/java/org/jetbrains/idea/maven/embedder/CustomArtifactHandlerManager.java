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
package org.jetbrains.idea.maven.embedder;

import gnu.trove.THashMap;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.DefaultArtifactHandlerManager;

import java.util.Map;

public class CustomArtifactHandlerManager extends DefaultArtifactHandlerManager {
  private final Map<String, ArtifactHandler> myCache = new THashMap<String, ArtifactHandler>();

  @Override
  public ArtifactHandler getArtifactHandler(String type) {
    ArtifactHandler result = myCache.get(type);
    if (result == null) {
      result = super.getArtifactHandler(type);
      if (result != null) myCache.put(type, result);
    }
    return result;
  }

  public void reset() {
    myCache.clear();
  }
}
