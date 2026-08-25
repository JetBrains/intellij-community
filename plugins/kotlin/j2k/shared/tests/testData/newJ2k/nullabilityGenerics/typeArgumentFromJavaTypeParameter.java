import org.jetbrains.annotations.NotNull;

public class Foo {
    void test(@NotNull String s) {
        J.unannotated(s);
        J.notNullTypeParameter(s);
        J.nullableTypeParameter(s);
        J.notNullBound(s);

        // the type argument is written explicitly: a different branch of getExplicitTypeArguments
        J.<String>notNullTypeParameter(s);

        // a null argument must keep the type argument nullable, or the result would not compile
        J.notNullTypeParameter(null);

        // conservative: the null in the V position also relaxes K
        J.twoTypeParameters(s, null);
    }
}
