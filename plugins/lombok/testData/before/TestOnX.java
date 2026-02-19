package action.delombok.onx;

import lombok.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

@ToString
@RequiredArgsConstructor(onConstructor_ = {@Inject, @Named("myName1")})
@AllArgsConstructor(onConstructor = @__({@Inject, @Named("myName2")})) //old syntax with @__
@EqualsAndHashCode(onParam_ = @Valid)
public class TestOnX {
    @Getter(onMethod_ = @Max(100))
    @NonNull
    private final Integer someIntField;

    @NonNull
    @Deprecated
    @Getter(onMethod_ = @Size(max = 20))
    @Setter(onMethod_ = @Size(min = 10), onParam_ = @Size(min = 15))
    private String someStringField;

    @With(onMethod_ = @NonNull, onParam_ = @Min(1))
    private float someFloatField;

    public static void main(String[] args) {
        final TestOnX test1 = new TestOnX(1, "str");
        System.out.println(test1);
        final TestOnX test2 = new TestOnX(2, "str", 3.0f);
        System.out.println(test2);
    }
}
