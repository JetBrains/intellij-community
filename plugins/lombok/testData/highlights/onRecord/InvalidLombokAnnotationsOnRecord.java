import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.With;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import lombok.experimental.UtilityClass;

<error descr="@Getter is only supported on a class, enum, or field type">@Getter</error>
<error descr="@Setter is only supported on a class or field type">@Setter</error>
<error descr="@Data is only supported on a class type">@Data</error>
<error descr="@Value is only supported on a class type">@Value</error>
<error descr="@ToString is only supported on a class or enum type">@ToString</error>
<error descr="@SuperBuilder is only supported on classes.">@SuperBuilder</error>
<error descr="@EqualsAndHashCode is only supported on a class type">@EqualsAndHashCode(callSuper = true)</error>
<error descr="@UtilityClass is only supported on a class (can't be an interface, enum, or annotation).">@UtilityClass</error>
<error descr="NoArgsConstructor is only supported on a class or an enum.">@NoArgsConstructor</error>
<error descr="AllArgsConstructor is only supported on a class or an enum.">@AllArgsConstructor</error>
<error descr="RequiredArgsConstructor is only supported on a class or an enum.">@RequiredArgsConstructor</error>
@With//OK on records
@Builder//OK on records
@FieldNameConstants//OK on records
public record InvalidLombokAnnotationsOnRecord(@Getter /*OK on record param*/int a, int b) {
}
