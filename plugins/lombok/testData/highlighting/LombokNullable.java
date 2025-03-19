package highlighting;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Data
@Builder
public class LombokNullable {
    @Nonnull
    private String value;

    public static void main(String[] args) {
        String value = Math.random() > 0.5 ? null : "xx";

        LombokNullable test = LombokNullable.builder()
                .value(<warning descr="Argument 'value' might be null">value</warning>)
                .build();

        System.out.println(test);
    }
}

@Data
class LombokNullableConstructor {
    @Nonnull
    private String value;

    @Builder
    public LombokNullableConstructor(@Nonnull String value) {
        this.value = value;
    }

    public static void main(String[] args) {
        String value = Math.random() > 0.5 ? null : "xx";

        LombokNullableConstructor test = LombokNullableConstructor.builder()
                .value(<warning descr="Argument 'value' might be null">value</warning>)
                .build();

        System.out.println(test);
    }
}

@Builder
record LombokNullableRecord(@Nonnull String foo) {
    public static LombokNullableRecord of(@Nullable String foo) {
        return LombokNullableRecord.builder()
                .foo(<warning descr="Argument 'foo' might be null">foo</warning>)
                .build();
    }

    public static void main(String[] args) {
      final LombokNullableRecord myNullableRecord = LombokNullableRecord.of(null);
      System.out.println(myNullableRecord);
    }
}