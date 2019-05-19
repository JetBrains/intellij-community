// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.Failure;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.containers.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MavenParsingContext {

  private final IntObjectMap<ArrayList<MavenExecutionEntry>> context = ContainerUtil.createConcurrentIntObjectMap();
  private int lastAddedThreadId;
  private final ExternalSystemTaskId myTaskId;

  public MavenParsingContext(ExternalSystemTaskId taskId) {
    myTaskId = taskId;
  }


  public ProjectExecutionEntry getProject(int threadId, Map<String, String> parameters, boolean create) {
    ProjectExecutionEntry currentProject =
      search(ProjectExecutionEntry.class, context.get(threadId),
             e -> parameters.get("id") == null || e.getName().equals(parameters.get("id")));

    if (currentProject == null && create) {
      currentProject = new ProjectExecutionEntry(parameters.get("id"), threadId);
      add(threadId, currentProject);
    }
    return currentProject;
  }


  public MojoExecutionEntry getMojo(int threadId, Map<String, String> parameters, boolean create) {
    return getMojo(threadId, parameters, parameters.get("goal"), create);
  }

  public MojoExecutionEntry getMojo(int threadId, Map<String, String> parameters, String name, boolean create) {
    if (name == null) {
      return null;
    }
    MojoExecutionEntry mojo = search(MojoExecutionEntry.class, context.get(threadId), e -> e.getName().equals(name));
    if (mojo == null && create) {
      ProjectExecutionEntry currentProject = getProject(threadId, parameters, false);
      mojo = new MojoExecutionEntry(name, threadId, currentProject);
      add(threadId, mojo);
    }
    return mojo;
  }

  public NodeExecutionEntry getNode(int threadId, String name, boolean create) {
    if (name == null) {
      return null;
    }
    NodeExecutionEntry node = search(NodeExecutionEntry.class, context.get(threadId), e -> e.getName().equals(name));

    if (node == null && create) {
      MojoExecutionEntry mojo = search(MojoExecutionEntry.class, context.get(threadId));
      node = new NodeExecutionEntry(name, threadId, mojo);
      add(threadId, mojo);
    }
    return node;
  }

  private void add(int id, MavenExecutionEntry entry) {
    ArrayList<MavenExecutionEntry> entries = context.get(id);
    if (entries == null) {
      entries = new ArrayList<>();
      context.put(id, entries);
    }
    lastAddedThreadId = id;
    entries.add(entry);
  }

  public Object getLastId() {
    ArrayList<MavenExecutionEntry> entries = context.get(lastAddedThreadId);
    if (entries == null || entries.isEmpty()) {
      return myTaskId;
    }
    return entries.get(entries.size() - 1).getId();
  }


  public class ProjectExecutionEntry extends MavenExecutionEntry {

    ProjectExecutionEntry(String name, int threadId) {
      super(name, threadId);
    }

    @Override
    public Object getParentId() {
      return MavenParsingContext.this.myTaskId;
    }
  }

  public class MojoExecutionEntry extends MavenExecutionEntry {

    private final ProjectExecutionEntry myProject;

    MojoExecutionEntry(String name,
                       int threadId,
                       ProjectExecutionEntry currentProject) {
      super(name, threadId);
      myProject = currentProject;
    }

    @Override
    public Object getParentId() {
      return myProject.getId();
    }
  }

  public class NodeExecutionEntry extends MavenExecutionEntry {

    private final MojoExecutionEntry myMojo;

    NodeExecutionEntry(String name,
                       int threadId,
                       MojoExecutionEntry mojo) {
      super(name, threadId);
      myMojo = mojo;
    }

    @Override
    public Object getParentId() {
      return myMojo.getId();
    }
  }


  private <T extends MavenExecutionEntry> T search(Class<T> klass,
                                                   ArrayList<MavenExecutionEntry> entries) {
    return search(klass, entries, e -> true);
  }

  @SuppressWarnings({"unchecked"})
  private <T extends MavenExecutionEntry> T search(Class<T> klass,
                                                   List<MavenExecutionEntry> entries,
                                                   Predicate<T> filter) {
    if (entries == null) {
      return null;
    }
    for (int j = entries.size() - 1; j >= 0; j--) {
      MavenExecutionEntry entry = entries.get(j);
      if (klass.isAssignableFrom(entry.getClass())) {
        if (filter.apply((T)entry)) {
          return (T)entry;
        }
      }
    }
    return null;
  }

  public abstract class MavenExecutionEntry {
    private final String myName;
    private final int myThreadId;
    private List<Failure> myFailures = null;
    private final Object myId = new Object();

    public MavenExecutionEntry(String name, int threadId) {
      myName = name;
      myThreadId = threadId;
    }

    public String getName() {
      return myName;
    }

    public List<Failure> getFailures() {
      return myFailures;
    }

    public void addFailure(Failure failure) {
      if (myFailures == null) {
        myFailures = new ArrayList<>();
      }
      myFailures.add(failure);
    }

    public void addFailures(List<Failure> failures) {
      if (myFailures == null) {
        myFailures = new ArrayList<>();
      }
      myFailures.addAll(failures);
    }

    public Object getId() {
      return myId;
    }

    public abstract Object getParentId();

    public void complete() {
      List<MavenExecutionEntry> entries = MavenParsingContext.this.context.get(myThreadId);
      if (entries != null) {
        entries.remove(this);
      }
    }
  }
}
