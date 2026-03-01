public class BuilderLombokCaller {

  public void useBuilder() {
    BuilderLombok book = BuilderLombok.builder()
      .title("Clean Code")
      .author("Robert C. Martin")
      .pages(464)
      .published(true)
      .build();
  }

  public BuilderLombok createBook(String title, String author) {
    return BuilderLombok.builder()
      .title(title)
      .author(author)
      .pages(100)
      .published(false)
      .build();
  }

  public BuilderLombok.BuilderLombokBuilder startBuilding() {
    return BuilderLombok.builder()
      .title("New Book");
  }
}
