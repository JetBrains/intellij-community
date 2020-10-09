import java.io.IOException;

public class BuilderMultipleConstructorException {

  private int first;
  private float second;

  public BuilderMultipleConstructorException(int i) throws IOException {
    throw new IOException("Some Exception");
  }

  public BuilderMultipleConstructorException(int i, String someString) throws IOException {
    throw new IOException("Some other Exception");
  }

  public BuilderMultipleConstructorException(int first, float second) {
    this.first = first;
    this.second = second;
  }

  public static void main(String[] args) {
    System.out.println(builder().first(2).second(2.0f).build());
  }

  public static BuilderMultipleConstructorExceptionBuilder builder() {
    return new BuilderMultipleConstructorExceptionBuilder();
  }

  public static class BuilderMultipleConstructorExceptionBuilder {
    private int first;
    private float second;

    BuilderMultipleConstructorExceptionBuilder() {
    }

    public BuilderMultipleConstructorExceptionBuilder first(int first) {
      this.first = first;
      return this;
    }

    public BuilderMultipleConstructorExceptionBuilder second(float second) {
      this.second = second;
      return this;
    }

    public BuilderMultipleConstructorException build() {
      return new BuilderMultipleConstructorException(first, second);
    }

    public String toString() {
      return "BuilderMultipleConstructorException.BuilderMultipleConstructorExceptionBuilder(first=" + this.first + ", second=" + this.second + ")";
    }
  }
}

