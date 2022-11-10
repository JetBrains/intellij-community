import lombok.Builder;

@Builder
public record FindUsageBuilderRecord(
  int foo,
  String b<caret>ar
) {

  public static void main(String[] args) {
    FindUsageBuilderRecord findUsageBuilderRecord = FindUsageBuilderRecord.builder()
      .bar("bar")
      .foo(1981)
      .build();

    System.out.println("Bar is: " + findUsageBuilderRecord.bar());
    System.out.println("Foo is: " + findUsageBuilderRecord.foo());
  }
}
