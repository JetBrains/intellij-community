package a

import a.EnumContainer.ENTRY_A

enum class Enum<caret>Container {
    ENTRY_A, ENTRY_B;
}

fun refer() {
    ENTRY_A
}