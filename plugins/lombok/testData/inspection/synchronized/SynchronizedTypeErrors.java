import lombok.Synchronized;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

interface SynchronizedInterface {
    <error descr="@Synchronized is legal only on methods in classes and enums.">@Synchronized</error>
    int makeSomething();
}

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@interface SynchronizedAnnotation {
    <error descr="@Synchronized is legal only on methods in classes and enums.">@Synchronized</error>
    String value() default "";
}

record SynchronizedRecord(int i) {
    <error descr="@Synchronized is legal only on methods in classes and enums.">@Synchronized</error>
    public String value() {
        return "something";
    }
}