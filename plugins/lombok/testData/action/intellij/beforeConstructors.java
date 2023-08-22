import lombok.*;

@AllArgsConstructor
class NewEntity extends MyBaseClass {
  private Long id;
  public NewEntity() {
    super();
  }
}

abstract class MyBase<caret>Class {
  public MyBaseClass() {}
}
