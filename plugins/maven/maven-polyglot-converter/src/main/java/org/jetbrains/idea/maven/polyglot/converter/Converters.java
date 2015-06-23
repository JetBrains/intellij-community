package org.jetbrains.idea.maven.polyglot.converter;

public enum Converters {
  NONE("pom.xml", new XmlMavenPolyglotConverter()),
  GROOVY("pom.groovy", new DefaultMavenPolyglotConverter("groovy", "org.sonatype.maven.polyglot.groovy.GroovyModelReader", "org.sonatype.maven.polyglot.groovy.builder.ModelBuilder"));

  private final String myPomFile;
  private final MavenPolyglotConverter myConverter;

  public String getPomFile() {
    return myPomFile;
  }

  public MavenPolyglotConverter getConverter() {
    return myConverter;
  }

  public static Converters fromPomFile(String pomFile) {
    for ( Converters type : values() ) {
      if ( type.getPomFile().equalsIgnoreCase(pomFile) ) {
        return type;
      }
    }
    // TODO
    return NONE;
  }

  Converters(String pomFile, MavenPolyglotConverter converter) {

    myPomFile = pomFile;
    myConverter = converter;
  }
}
