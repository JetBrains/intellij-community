import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.TYPE_USE})
public @interface NonNls {
}

interface I {
    @NonNls String str = "hello";
}

class C {
    static final @NonNls String BLADE = "Blade";
}