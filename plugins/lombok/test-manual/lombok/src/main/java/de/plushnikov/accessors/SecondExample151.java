package de.plushnikov.accessors;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(prefix = "m")
@Builder
public class SecondExample151 {

    public static class SecondExample151Builder {
        private String field = "default";
    }

    private final String mField;

    public static void main(String[] args) {
        System.out.println(builder().field("a").build());
    }
}
