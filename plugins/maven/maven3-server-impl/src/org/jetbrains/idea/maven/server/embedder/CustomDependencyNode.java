// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Edoardo Luppi
 */
class CustomDependencyNode implements DependencyNode {
  private final DependencyNode myDelegate;

  CustomDependencyNode(@NotNull final DependencyNode delegate) {
    myDelegate = delegate;
  }

  @Override
  public List<DependencyNode> getChildren() {
    // noinspection SSBasedInspection
    return myDelegate.getChildren()
      .stream()
      .map(CustomDependencyNode::new)
      .collect(Collectors.toList());
  }

  @Override
  public void setChildren(List<DependencyNode> list) {
    myDelegate.setChildren(list);
  }

  @Override
  public Dependency getDependency() {
    final Map<?, ?> data = getData();

    if (data != null) {
      final DependencyNode winner = (DependencyNode)data.get("conflict.winner");

      if (winner != null) {
        return winner.getDependency();
      }
    }

    return myDelegate.getDependency();
  }

  @Override
  public Artifact getArtifact() {
    return myDelegate.getArtifact();
  }

  @Override
  public void setArtifact(final Artifact artifact) {
    myDelegate.setArtifact(artifact);
  }

  @Override
  public List<? extends Artifact> getRelocations() {
    return myDelegate.getRelocations();
  }

  @Override
  public Collection<? extends Artifact> getAliases() {
    return myDelegate.getAliases();
  }

  @Override
  public VersionConstraint getVersionConstraint() {
    return myDelegate.getVersionConstraint();
  }

  @Override
  public Version getVersion() {
    return myDelegate.getVersion();
  }

  @Override
  public void setScope(final String scope) {
    myDelegate.setScope(scope);
  }

  @Override
  public void setOptional(final Boolean optional) {
    myDelegate.setOptional(optional);
  }

  @Override
  public int getManagedBits() {
    return myDelegate.getManagedBits();
  }

  @Override
  public List<RemoteRepository> getRepositories() {
    return myDelegate.getRepositories();
  }

  @Override
  public String getRequestContext() {
    return myDelegate.getRequestContext();
  }

  @Override
  public void setRequestContext(final String requestContext) {
    myDelegate.setRequestContext(requestContext);
  }

  @Override
  public Map<?, ?> getData() {
    return myDelegate.getData();
  }

  @Override
  public void setData(final Map<Object, Object> data) {
    myDelegate.setData(data);
  }

  @Override
  public void setData(final Object key, final Object value) {
    myDelegate.setData(key, value);
  }

  @Override
  public boolean accept(final DependencyVisitor visitor) {
    if (visitor.visitEnter(this)) {
      for (final DependencyNode child : getChildren()) {
        if (!child.accept(visitor)) {
          break;
        }
      }
    }

    return visitor.visitLeave(this);
  }
}
