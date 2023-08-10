import java.lang.annotation.*;
import java.util.*;

@Target({ElementType.TYPE_USE})
@interface TA { }

class Test {
    @TA List<@TA String> foo() {
        return null;
    }
}