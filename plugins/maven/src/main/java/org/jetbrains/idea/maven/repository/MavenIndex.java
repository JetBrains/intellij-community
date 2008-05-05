package org.jetbrains.idea.maven.repository;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.File;

public class MavenIndex {
  public enum Kind { LOCAL(1), PROJECT(1), REMOTE(2);
    private int code;
    Kind(int code) {
      this.code = code;
    }

    public int getCode() {
      return code;
    }

    public static Kind forCode(int code) {
      for (Kind each : values()) {
        if (each.code == code) return each;
      }
      throw new RuntimeException("Unknown index kind: " + code);
    }
  }

  private String myId;
  private String myRepositoryPathOrUrl;
  private Kind myKind;

  public MavenIndex(String id, String repositoryPathOrUrl, Kind kind) {
    set(id, repositoryPathOrUrl, kind);
  }

  public void set(String id, String repositoryPathOrUrl, Kind kind) {
    myId = id;
    myRepositoryPathOrUrl = repositoryPathOrUrl;
    myKind = kind;
  }

  public MavenIndex(DataInputStream s) throws IOException {
    this(s.readUTF(), s.readUTF(), Kind.forCode(s.readInt()));
  }

  public void write(DataOutputStream s) throws IOException {
    s.writeUTF(myId);
    s.writeUTF(myRepositoryPathOrUrl);
    s.writeInt(myKind.getCode());
  }

  public String getId() {
    return myId;
  }

  public File getRepositoryFile() {
    return myKind == Kind.LOCAL ? new File(myRepositoryPathOrUrl) : null;
  }

  public String getRepositoryUrl() {
    return myKind == Kind.REMOTE ? myRepositoryPathOrUrl : null;
  }

  public String getRepositoryPathOrUrl() {
    return myRepositoryPathOrUrl;
  }

  public Kind getKind() {
    return myKind;
  }
}
