package org.jetbrains.idea.maven.repository;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.File;

public class MavenRepositoryInfo {
  private String id;
  private String repositoryPathOrUrl;
  private boolean isRemote;

  public MavenRepositoryInfo(String id, String repositoryPathOrUrl, boolean isRemote) {
    set(id, repositoryPathOrUrl, isRemote);
  }

  public void set(String id, String repositoryPathOrUrl, boolean isRemote) {
    this.id = id;
    this.repositoryPathOrUrl = repositoryPathOrUrl;
    this.isRemote = isRemote;
  }

  public MavenRepositoryInfo(DataInputStream s) throws IOException {
    this(s.readUTF(), s.readUTF(),  s.readBoolean());
  }

  public void write(DataOutputStream s) throws IOException {
    s.writeUTF(id);
    s.writeUTF(repositoryPathOrUrl);
    s.writeBoolean(isRemote);
  }

  public String getId() {
    return id;
  }

  public File getRepositoryFile() {
    return isRemote ? null : new File(repositoryPathOrUrl);
  }

  public String getRepositoryUrl() {
    return isRemote ? repositoryPathOrUrl : null;
  }

  public String getRepositoryPathOrUrl() {
    return repositoryPathOrUrl;
  }

  public boolean isRemote() {
    return isRemote;
  }
}
