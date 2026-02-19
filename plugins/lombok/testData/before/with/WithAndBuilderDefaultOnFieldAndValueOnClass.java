import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.List;

@Value
@Builder
class WithAndBuilderDefaultOnFieldAndValueOnClass {

    String field1;

    @With
    @Builder.Default
    List<String> field2 = List.of();

    public static void main(String[] args) {
        final WithAndBuilderDefaultOnFieldAndValueOnClass build = WithAndBuilderDefaultOnFieldAndValueOnClass.builder()
                .field1("1")
                .field2(List.of("2"))
                .build();

        final WithAndBuilderDefaultOnFieldAndValueOnClass field2 = build.withField2(List.of("3"));
        System.out.println(field2);
    }
}
