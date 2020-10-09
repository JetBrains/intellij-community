import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.*;

@Builder
@Getter
@Accessors(prefix = "m")
public class FindUsageSingularBuilder {

  @Singular("fooDate")
  private Map<Integer, Date> mFooDateMap;

  @Singular
  private List<String> mB<caret>ars;

  public static void main(String[] args) {
    FindUsageSingularBuilder findUsageBuilder = FindUsageSingularBuilder.builder()
      .bar("bar")
      .fooDate(16061981, new Date());

    findUsageBuilder = FindUsageSingularBuilder.builder()
      .bars(Arrays.asList("bar1", "bar2"))
      .fooDateMap(Collections.emptyMap());

    findUsageBuilder = FindUsageSingularBuilder.builder()
      .clearBars()
      .clearFooDateMap()
      .build();

    System.out.println("Bar is: " + findUsageBuilder.getBars());
    System.out.println("Foo is: " + findUsageBuilder.getFooDateMap());
  }
}
