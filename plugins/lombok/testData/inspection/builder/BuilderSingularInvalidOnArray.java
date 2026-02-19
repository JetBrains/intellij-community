//skip compare content
<error descr="Lombok cannot generate singular-form builder methods for type 'java.lang.String[]'">@lombok.Builder</error>
@lombok.ToString
public class BuilderSingularInvalidOnArray {
    private String foo;

    @lombok.Singular
    private java.util.List<String> bars;

    @lombok.Singular
    private String[] fooBars;
}