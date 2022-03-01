// "Add remaining branches" "true"
// ERROR: 'when' expression must be exhaustive, add necessary 'RED', 'GREEN', 'BLUE' branches or 'else' branch instead

package u

import e.OwnEnum
import e.getOwnEnum

fun mainContext() {
    val ownLocal = getOwnEnum()
    when (ownLocal) {
        OwnEnum.RED -> TODO()
        OwnEnum.GREEN -> TODO()
        OwnEnum.BLUE -> TODO()
    }
}