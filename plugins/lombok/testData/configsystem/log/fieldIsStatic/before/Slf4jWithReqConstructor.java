import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Slf4jWithReqConstructor {
  private final String sampleField;

  public static void main(String... args) {
    new Slf4jWithReqConstructor("demo");
  }
}
