package org.jetbrains.idea.maven.embedder;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.DefaultArtifactResolver;
import org.apache.maven.project.build.model.DefaultModelLineageBuilder;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

public class CustomArtifactResolver extends DefaultArtifactResolver implements Contextualizable {
  public static final String MAVEN_PROJECT_MODEL_MANAGER = "MAVEN_PROJECT_MODEL_MANAGER";
  public static final String IS_ONLINE = "IS_ONLINE";

  private MavenProjectsTree myModelManager;
  private boolean isOnline;
  private CustomWagonManager myWagonManagerCache;

  public void contextualize(Context context) throws ContextException {
    myModelManager = (MavenProjectsTree)context.get(MAVEN_PROJECT_MODEL_MANAGER);
    isOnline = (Boolean)context.get(IS_ONLINE);
  }

  @Override
  public void resolve(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
      throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveAsModule(artifact)) return;

    boolean shouldOpen = !isOnline && isResolvingParent();
    if (shouldOpen) open();
    try {
      super.resolve(artifact, remoteRepositories, localRepository);
    }
    finally {
      if (shouldOpen) close();
    }
  }

  @Override
  public void resolveAlways(Artifact artifact, List remoteRepositories, ArtifactRepository localRepository)
      throws ArtifactResolutionException, ArtifactNotFoundException {
    if (resolveAsModule(artifact)) return;

    boolean shouldOpen = !isOnline && isResolvingParent();
    if (shouldOpen) open();
    try {
      super.resolveAlways(artifact, remoteRepositories, localRepository);
    }
    finally {
      if (shouldOpen) close();
    }
  }

  private boolean isResolvingParent() {
    StackTraceElement[] stack = new Throwable().getStackTrace();
    for (StackTraceElement each : stack) {
      if (each.getClassName().equals(DefaultModelLineageBuilder.class.getName())
          && each.getMethodName().equals("resolveParentPom")) {
        return true;
      }
    }
    return false;
  }

  private boolean resolveAsModule(Artifact a) {
    MavenProjectModel project = myModelManager.findProject(a);
    if (project == null) return false;

    a.setResolved(true);
    a.setFile(new File(project.getPath()));

    return true;
  }

  public void open() {
    getWagonManager().open();
  }

  public void close() {
    getWagonManager().close();
  }

  private CustomWagonManager getWagonManager() {
    if (myWagonManagerCache == null) {
      Object wagon = getFieldValue(DefaultArtifactResolver.class, "wagonManager", this);
      myWagonManagerCache = (CustomWagonManager)wagon;
    }
    return myWagonManagerCache;
  }

  private Object getFieldValue(Class c, String fieldName, Object o) {
    try {
      Field f = c.getDeclaredField(fieldName);
      f.setAccessible(true);
      return f.get(o);
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}