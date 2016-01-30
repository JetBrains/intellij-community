package de.plushnikov.builder.bugs;

@lombok.Builder
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class BuilderAndAllArgsConstructor {

    private String field1;
    private String field2;

    public static void main(String[] args) {
        BuilderAndAllArgsConstructor constructor = new BuilderAndAllArgsConstructor("", "");
        System.out.println(constructor);
    }
}
