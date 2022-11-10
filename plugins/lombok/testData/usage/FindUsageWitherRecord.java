import lombok.Value;
import lombok.experimental.Wither;

@Wither
@Value
public record FindUsageWitherRecord(
  int foo,
  tring b<caret>ar
) {

  public static void main(String[] args) {
    FindUsageWitherRecord findUsageWitherRecord = new FindUsageWitherRecord(1, "bar");
    findUsageWitherRecord
      .withBar("myBar")
      .withFoo(1981);
    System.out.println("Bar is: " + findUsageWitherRecord.bar());
    System.out.println("Foo is: " + findUsageWitherRecord.foo());
  }
}
