// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: the Kotlin Lombok compiler plugin generates these members, so J2K must keep the annotations
package test;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class WithLogger {
    private String name;
}

@ToString
@EqualsAndHashCode
class WithGeneratedMembers {
    private String name;
}

@NoArgsConstructor
class WithNoArgsConstructor {
    private String name;
}

@Builder
class WithBuilder {
    private String name;

    private int age;
}

@SuperBuilder
class WithSuperBuilder {
    private String name;
}

@Getter
class WithStandaloneGetter {
    private String name;
}
