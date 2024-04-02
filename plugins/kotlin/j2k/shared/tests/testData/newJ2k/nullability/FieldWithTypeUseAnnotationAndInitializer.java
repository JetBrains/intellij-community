// IGNORE_K2
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.TYPE_USE})
public @interface NonNls {
}

public interface I {
    @NonNls String str = "hello";
}