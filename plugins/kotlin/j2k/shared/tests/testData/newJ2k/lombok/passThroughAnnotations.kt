// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: the Kotlin Lombok compiler plugin generates these members, so J2K must keep the annotations
package test

import lombok.Builder
import lombok.EqualsAndHashCode
import lombok.Getter
import lombok.NoArgsConstructor
import lombok.ToString
import lombok.experimental.SuperBuilder
import lombok.extern.slf4j.Slf4j

@Slf4j
internal class WithLogger {
    private val name: String? = null
}

@ToString
@EqualsAndHashCode
internal class WithGeneratedMembers {
    private val name: String? = null
}

@NoArgsConstructor
internal class WithNoArgsConstructor {
    private val name: String? = null
}

@Builder
internal class WithBuilder {
    private val name: String? = null

    private val age = 0
}

@SuperBuilder
internal class WithSuperBuilder {
    private val name: String? = null
}

@Getter
internal class WithStandaloneGetter {
    private val name: String? = null
}
