import java.util.regex.Pattern;
class Tolerate {
  private Pattern pattern;
  public @lombok.experimental.Tolerate void setPattern(String pattern) {
    setPattern(Pattern.compile(pattern));
  }
  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") void setPattern(final Pattern pattern) {
    this.pattern = pattern;
  }
  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") Pattern getPattern() {
    return this.pattern;
  }
}
class Tolerate2 {
  private final Pattern pattern;
  public Tolerate2 withPattern(String pattern) {
    return withPattern(Pattern.compile(pattern));
  }
  public Tolerate2 withPattern(String nameGlob, String extensionGlob) {
    return withPattern(nameGlob.replace("*", ".*") + "\\." + extensionGlob.replace("*", ".*"));
  }

  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") Pattern getPattern() {
    return this.pattern;
  }
  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") Tolerate2 withPattern(final Pattern pattern) {
    return this.pattern == pattern ? this : new Tolerate2(pattern);
  }
  public @java.beans.ConstructorProperties({"pattern"}) @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") Tolerate2(final Pattern pattern) {
    this.pattern = pattern;
  }
}
