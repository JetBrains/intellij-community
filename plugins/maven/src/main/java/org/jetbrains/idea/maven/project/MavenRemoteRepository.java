package org.jetbrains.idea.maven.project;

import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;

import java.io.Serializable;

public class MavenRemoteRepository implements Serializable {
  private String myId;
  private String myName;
  private String myUrl;
  private String myLayout;
  private Policy myReleasesPolicy;
  private Policy mySnapshotsPolicy;

  protected MavenRemoteRepository() {
  }

  public MavenRemoteRepository(Repository repository) {
    myId = repository.getId();
    myName = repository.getName();
    myUrl = repository.getUrl();
    myLayout = repository.getLayout();

    if (repository.getReleases()!= null) myReleasesPolicy = new Policy(repository.getReleases());
    if (repository.getSnapshots() != null) mySnapshotsPolicy = new Policy(repository.getSnapshots());
  }

  public String getId() {
    return myId;
  }

  public String getName() {
    return myName;
  }

  public String getUrl() {
    return myUrl;
  }

  public String getLayout() {
    return myLayout;
  }

  public Policy getReleasesPolicy() {
    return myReleasesPolicy;
  }

  public Policy getSnapshotsPolicy() {
    return mySnapshotsPolicy;
  }

  public Repository toRepository() {
    Repository result = new Repository();
    result.setId(myId);
    result.setName(myName);
    result.setUrl(myUrl);
    result.setLayout(myLayout);

    if (myReleasesPolicy != null) result.setReleases(myReleasesPolicy.toRepositoryPolicy());
    if (mySnapshotsPolicy != null) result.setSnapshots(mySnapshotsPolicy.toRepositoryPolicy());

    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenRemoteRepository that = (MavenRemoteRepository)o;

    if (myId != null ? !myId.equals(that.myId) : that.myId != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myId != null ? myId.hashCode() : 0;
  }

  public static class Policy implements Serializable {
    private boolean myEnabled;
    private String myUpdatePolicy;
    private String myChecksumPolicy;

    protected Policy() {
    }

    public Policy(RepositoryPolicy policy) {
      myEnabled = policy.isEnabled();
      myUpdatePolicy = policy.getUpdatePolicy();
      myChecksumPolicy = policy.getChecksumPolicy();
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public String getUpdatePolicy() {
      return myUpdatePolicy;
    }

    public String getChecksumPolicy() {
      return myChecksumPolicy;
    }

    public RepositoryPolicy toRepositoryPolicy() {
      RepositoryPolicy result =new RepositoryPolicy();
      result.setEnabled(myEnabled);
      result.setUpdatePolicy(myUpdatePolicy);
      result.setChecksumPolicy(myChecksumPolicy);
      return result;
    }
  }
}
