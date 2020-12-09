import lombok.Value;
import lombok.Builder;

@Value
@Builder
public class ValueBuilder {
  String o1;
  private int o2;
  private final double o3;

  public static void main(String[] args) {
    ValueBuilder builder = new ValueBuilder("1", 2, 3.0);
    System.out.println(builder);
  }
}
