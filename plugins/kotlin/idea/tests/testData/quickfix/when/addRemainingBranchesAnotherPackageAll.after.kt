// "Add remaining branches with * import" "true"
// ERROR: 'when' expression must be exhaustive, add necessary 'RED', 'GREEN', 'BLUE' branches or 'else' branch instead

package u

import e.OwnEnum.*
import e.getOwnEnum

fun mainContext() {
    val ownLocal = getOwnEnum()
    <selection><caret></selection>when (ownLocal) {
        RED -> TODO()
        GREEN -> TODO()
        BLUE -> TODO()
    }
}