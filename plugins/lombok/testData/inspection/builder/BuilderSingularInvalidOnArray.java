//skip compare content
<error descr="Lombok does not know how to create the singular-form builder methods for type 'java.lang.String[]'; they wont be generated.">@lombok.Builder</error>
@lombok.ToString
public class BuilderSingularInvalidOnArray {
    private String foo;

    @lombok.Singular
    private java.util.List<String> bars;

    @lombok.Singular
    private String[] fooBars;
}