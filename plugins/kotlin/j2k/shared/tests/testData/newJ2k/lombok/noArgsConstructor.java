// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: J2K does not convert @NoArgsConstructor yet, so this file pins what it does today
package test;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@NoArgsConstructor
class OnlyNoArgs {
    private String mutable;
}

@NoArgsConstructor
@RequiredArgsConstructor
class NoArgsAndRequiredArgs {
    private final String required;
}

@NoArgsConstructor
@Data
class NoArgsAndData {
    private final String required;
}

@NoArgsConstructor(force = true)
class ForcedNoArgs {
    private final String forced;
}

@NoArgsConstructor(staticName = "of")
class StaticNameNoArgs {
    private String mutable;
}
