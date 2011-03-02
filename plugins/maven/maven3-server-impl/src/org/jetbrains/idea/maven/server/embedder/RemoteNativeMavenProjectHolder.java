package org.jetbrains.idea.maven.server.embedder;

import com.intellij.util.containers.WeakValueHashMap;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

public class RemoteNativeMavenProjectHolder implements NativeMavenProjectHolder {
  private static final WeakValueHashMap<Integer, RemoteNativeMavenProjectHolder> myMap =
    new WeakValueHashMap<Integer, RemoteNativeMavenProjectHolder>();

  private final MavenProject myMavenProject;

  public RemoteNativeMavenProjectHolder(@NotNull MavenProject mavenProject) {
    myMavenProject = mavenProject;
    myMap.put(getId(), this);
  }

  public int getId() {
    return System.identityHashCode(this);
  }

  @NotNull
  public static MavenProject findProjectById(int id) {
    RemoteNativeMavenProjectHolder result = myMap.get(id);
    if (result == null) {
      throw new RuntimeException("NativeMavenProjectHolder not found for id: " + id);
    }
    return result.myMavenProject;
  }
}
