// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-19423: J2K does not convert @NoArgsConstructor yet, so this file pins what it does today
package test

import lombok.NoArgsConstructor

@NoArgsConstructor
internal class OnlyNoArgs {
    private val mutable: String? = null
}

@NoArgsConstructor
internal class NoArgsAndRequiredArgs(private val required: String)

@NoArgsConstructor
internal data class NoArgsAndData(val required: String)

@NoArgsConstructor(force = true)
internal class ForcedNoArgs {
    private val forced: String? = null
}

@NoArgsConstructor(staticName = "of")
internal class StaticNameNoArgs {
    private val mutable: String? = null
}
