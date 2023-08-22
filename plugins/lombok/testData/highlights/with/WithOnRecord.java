import lombok.Builder;
import lombok.With;

public class WithOnRecord {
  public static void main(String[] args) {
    WithTest withTest = WithTest.builder().build();
    WithTest errorLine = withTest.withDetail("test");
    System.out.println(errorLine);
  }

  @Builder
  @With
  public record WithTest(String detail) {
    public static WithTest ERROR = builder()
      .detail("detail")
      .build();
  }
}
