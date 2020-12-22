import java.io.IOException;

import lombok.AllArgsConstructor;
import lombok.Builder;

import java.io.IOException;

@Builder
@AllArgsConstructor
public class BuilderMultipleConstructorException {

  private int first;
  private float second;

  public BuilderMultipleConstructorException(int i) throws IOException {
    throw new IOException("Some Exception");
  }

  public BuilderMultipleConstructorException(int i, String someString) throws IOException {
    throw new IOException("Some other Exception");
  }

  public static void main(String[] args) {
    System.out.println(builder().first(2).second(2.0f).build());
  }
}
