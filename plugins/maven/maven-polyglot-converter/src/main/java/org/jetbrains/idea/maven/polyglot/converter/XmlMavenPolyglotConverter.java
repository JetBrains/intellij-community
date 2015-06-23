package org.jetbrains.idea.maven.polyglot.converter;

public class XmlMavenPolyglotConverter implements MavenPolyglotConverter {
  @Override
  public String convert(String pomFile) {
    return pomFile;
  }
}
