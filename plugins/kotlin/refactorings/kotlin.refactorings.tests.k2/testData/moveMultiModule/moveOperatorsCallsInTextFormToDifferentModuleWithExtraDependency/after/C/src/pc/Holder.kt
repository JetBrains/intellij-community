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
        basicMem plus Bar()
        basicMem minus Bar()
        basicMem times Bar()
        basicMem div Bar()
        basicMem rem Bar()

        basicMem.plus(Bar())
        basicMem.minus(Bar())
        basicMem.times(Bar())
        basicMem.div(Bar())
        basicMem.rem(Bar())

        basicExt plus Bar()
        basicExt minus Bar()
        basicExt times Bar()
        basicExt div Bar()
        basicExt rem Bar()

        basicExt.plus(Bar())
        basicExt.minus(Bar())
        basicExt.times(Bar())
        basicExt.div(Bar())
        basicExt.rem(Bar())

        augmentedMem plusAssign Bar()
        augmentedMem minusAssign Bar()
        augmentedMem timesAssign Bar()
        augmentedMem divAssign Bar()
        augmentedMem remAssign Bar()

        augmentedMem.plusAssign(Bar())
        augmentedMem.minusAssign(Bar())
        augmentedMem.timesAssign(Bar())
        augmentedMem.divAssign(Bar())
        augmentedMem.remAssign(Bar())

        augmentedExt plusAssign Bar()
        augmentedExt minusAssign Bar()
        augmentedExt timesAssign Bar()
        augmentedExt divAssign Bar()
        augmentedExt remAssign Bar()

        augmentedExt.plusAssign(Bar())
        augmentedExt.minusAssign(Bar())
        augmentedExt.timesAssign(Bar())
        augmentedExt.divAssign(Bar())
        augmentedExt.remAssign(Bar())
    }
}