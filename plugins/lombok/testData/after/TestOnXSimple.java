package action.delombok.onx;

import lombok.RequiredArgsConstructor;
import lombok.NonNull;

import javax.inject.Inject;
import javax.inject.Named;

public class TestOnX {
  @NonNull
  private final Integer someIntField;

  @Named("myName1")
  @Inject
  public TestOnX(@NonNull Integer someIntField) {
    this.someIntField = someIntField;
  }
}