// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: @Getter and @Setter on a field change the visibility that @Data would generate
package test;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class Levels {
    private String defaultLevels;

    @Getter(AccessLevel.PROTECTED)
    private String protectedGetter;

    @Getter(AccessLevel.PACKAGE)
    private String packageGetter;

    @Getter(AccessLevel.PRIVATE)
    private String privateGetter;

    @Getter(AccessLevel.NONE)
    private String noGetter;

    @Setter(AccessLevel.NONE)
    private String noSetter;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String noAccessors;

    @Getter
    private String explicitDefaultGetter;

    private final String finalField;
}

@Data
@Getter(AccessLevel.PROTECTED)
class ClassLevelGetter {
    private String fromClassLevel;

    @Getter(AccessLevel.PUBLIC)
    private String fieldOverridesClass;
}
