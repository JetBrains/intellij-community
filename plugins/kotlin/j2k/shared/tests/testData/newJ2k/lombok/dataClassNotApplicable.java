// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: Kotlin refuses `data` on these classes, so only the constructor may change
package test;

import lombok.Data;

@Data
abstract class AbstractPojo {
    private final int abstractField;
}

@Data
class NoRequiredFieldPojo {
    private int justMutable;
}

@Data(staticConstructor = "of")
class StaticConstructorPojo {
    private final int withStaticConstructor;
}

@Data
class PojoWithOwnConstructor {
    private final int own;

    PojoWithOwnConstructor(int own) {
        this.own = own;
    }
}

@Data
enum DataEnum {
    A, B
}

class Outer {
    @Data
    class InnerPojo {
        private final int innerField;
    }
}
