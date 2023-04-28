package org.jetbrains.idea.maven.server;

import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public class RemoteNativeMaven3ProjectHolder implements NativeMavenProjectHolder {
  private static final Map<Integer, Reference<RemoteNativeMaven3ProjectHolder>> myMap = new HashMap<Integer, Reference<RemoteNativeMaven3ProjectHolder>>();

  private final MavenProject myMavenProject;

  public RemoteNativeMaven3ProjectHolder(@NotNull MavenProject mavenProject) {
    myMavenProject = mavenProject;
    myMap.put(getId(), new WeakReference<RemoteNativeMaven3ProjectHolder>(this));
  }

  @Override
  public int getId() {
    return System.identityHashCode(this);
  }

  @NotNull
  public static MavenProject findProjectById(int id) {
    Reference<RemoteNativeMaven3ProjectHolder> reference = myMap.get(id);
    RemoteNativeMaven3ProjectHolder result = reference == null ? null : reference.get();
    if (result == null) {
      throw new RuntimeException("NativeMavenProjectHolder not found for id: " + id);
    }
    return result.myMavenProject;
  }
}
