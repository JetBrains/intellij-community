public class BuilderPlainJava {
  private String title;
  private String author;
  private int pages;
  private boolean published;

  BuilderPlainJava(String title, String author, int pages, boolean published) {
    this.title = title;
    this.author = author;
    this.pages = pages;
    this.published = published;
  }

  public static BuilderPlainJavaBuilder builder() {
    return new BuilderPlainJavaBuilder();
  }

  public static class BuilderPlainJavaBuilder {
    private String title;
    private String author;
    private int pages;
    private boolean published;

    BuilderPlainJavaBuilder() {
    }

    public BuilderPlainJavaBuilder title(String title) {
      this.title = title;
      return this;
    }

    public BuilderPlainJavaBuilder author(String author) {
      this.author = author;
      return this;
    }

    public BuilderPlainJavaBuilder pages(int pages) {
      this.pages = pages;
      return this;
    }

    public BuilderPlainJavaBuilder published(boolean published) {
      this.published = published;
      return this;
    }

    public BuilderPlainJava build() {
      return new BuilderPlainJava(title, author, pages, published);
    }
  }
}
