package pb

import pa.*

class <caret>Holder {
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