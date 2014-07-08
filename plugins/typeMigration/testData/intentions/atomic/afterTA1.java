// "Convert to atomic" "true"
import java.lang.annotation.*;
import java.util.concurrent.atomic.AtomicReference;

@Target(value = ElementType.TYPE_USE)
public @interface TA { int value(); }

class T {
    final AtomicReference<@TA(42) String> v = new AtomicReference<String>();
}