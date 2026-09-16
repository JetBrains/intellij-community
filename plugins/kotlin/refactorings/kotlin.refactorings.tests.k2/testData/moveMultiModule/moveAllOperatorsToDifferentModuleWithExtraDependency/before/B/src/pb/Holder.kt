package pb

import pa.*

class <caret>Holder {
    var basicMem: BasicOperatorMembers = BasicOperatorMembers()
    var basicExt: BasicOperatorExtensions = BasicOperatorExtensions()
    var augmentedMem: AugmentedOperatorMembers = AugmentedOperatorMembers()
    var augmentedExt: AugmentedOperatorExtensions = AugmentedOperatorExtensions()

    fun test() {
        basicMem + Bar()
        basicMem - Bar()
        basicMem * Bar()
        basicMem / Bar()
        basicMem % Bar()

        basicExt + Bar()
        basicExt - Bar()
        basicExt * Bar()
        basicExt / Bar()
        basicExt % Bar()

        basicMem += Bar()
        basicMem -= Bar()
        basicMem *= Bar()
        basicMem /= Bar()
        basicMem %= Bar()

        basicExt += Bar()
        basicExt -= Bar()
        basicExt *= Bar()
        basicExt /= Bar()
        basicExt %= Bar()

        augmentedMem += Bar()
        augmentedMem -= Bar()
        augmentedMem *= Bar()
        augmentedMem /= Bar()
        augmentedMem %= Bar()

        augmentedExt += Bar()
        augmentedExt -= Bar()
        augmentedExt *= Bar()
        augmentedExt /= Bar()
        augmentedExt %= Bar()
    }
}
