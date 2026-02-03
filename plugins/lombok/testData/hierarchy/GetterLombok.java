import lombok.Getter;

@Getter
public class GetterLombok {
  private String name;
  private int age;

  public GetterLombok(String name, int age) {
    this.name = name;
    this.age = age;
  }
}
