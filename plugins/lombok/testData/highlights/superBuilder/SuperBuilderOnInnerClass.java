package highlighting;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public class SuperBuilderOnInnerClass {

    <error descr="@SuperBuilder is not supported on non-static nested classes.">@SuperBuilder</error>
    class NonStaticInnerClass {
        private int anInt;
    }

    @SuperBuilder
    static class StaticInnerClass {
        private int anInt;
    }
}
