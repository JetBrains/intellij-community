import java.util.Objects;

class Test<caret> {
  private String value;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Test test = (Test)o;
    return Objects.equals(value, test.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
