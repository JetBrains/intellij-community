import java.lang.annotation.*;

@Target(ElementType.TYPE_USE)
@interface Ann {
}

@Ann class  C<@Ann T> {
    void foo(@Ann String s) {}
}
@Ann @interface Ann2 {}