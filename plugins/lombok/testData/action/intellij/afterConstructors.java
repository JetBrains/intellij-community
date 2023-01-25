import lombok.*;

@AllArgsConstructor
class NewEntity extends MyBaseClass1 {
  private Long id;
  public NewEntity() {
    super();
  }
}

abstract class MyBase<caret>Class1 {
  public MyBaseClass1() {}
}
