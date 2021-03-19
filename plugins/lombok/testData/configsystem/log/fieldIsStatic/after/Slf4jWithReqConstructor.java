
public class Slf4jWithReqConstructor {
  private final String sampleField;

  private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Slf4jWithReqConstructor.class);

  public Slf4jWithReqConstructor(String sampleField) {
    this.sampleField = sampleField;
  }

  public static void main(String... args) {
    new Slf4jWithReqConstructor("demo");
  }
}
