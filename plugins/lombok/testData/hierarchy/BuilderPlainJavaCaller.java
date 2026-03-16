public class BuilderPlainJavaCaller {

  public void useBuilder() {
    BuilderPlainJava book = BuilderPlainJava.builder()
      .title("Clean Code")
      .author("Robert C. Martin")
      .pages(464)
      .published(true)
      .build();
  }

  public BuilderPlainJava createBook(String title, String author) {
    return BuilderPlainJava.builder()
      .title(title)
      .author(author)
      .pages(100)
      .published(false)
      .build();
  }

  public BuilderPlainJava.BuilderPlainJavaBuilder startBuilding() {
    return BuilderPlainJava.builder()
      .title("New Book");
  }
}
