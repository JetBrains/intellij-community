import java.lang.annotation.*;

@Target({ElementType.TYPE_USE})
@interface TA { }

class Test {
    Wrapper foo() {
        return new Wrapper(null);
    }
}