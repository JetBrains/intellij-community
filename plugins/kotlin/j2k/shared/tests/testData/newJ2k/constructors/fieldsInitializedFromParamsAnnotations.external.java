import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@interface FieldOnly {
}

// The shape of JPA @Column / @Id: METHOD maps to a getter, never to the property itself.
@Target({ElementType.METHOD, ElementType.FIELD})
@interface FieldAndMethod {
}

@Target({ElementType.FIELD, ElementType.PARAMETER})
@interface FieldAndParam {
}
