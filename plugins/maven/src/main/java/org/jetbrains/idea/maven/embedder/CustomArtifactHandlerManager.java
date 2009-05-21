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
