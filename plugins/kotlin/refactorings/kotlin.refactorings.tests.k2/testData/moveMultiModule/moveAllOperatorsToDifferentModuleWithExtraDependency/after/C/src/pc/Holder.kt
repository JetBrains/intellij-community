package pc

import pa.AugmentedOperatorExtensions
import pa.AugmentedOperatorMembers
import pa.Bar
import pa.BasicOperatorExtensions
import pa.BasicOperatorMembers
import pa.div
import pa.divAssign
import pa.minus
import pa.minusAssign
import pa.plus
import pa.plusAssign
import pa.rem
import pa.remAssign
import pa.times
import pa.timesAssign

class Holder {
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