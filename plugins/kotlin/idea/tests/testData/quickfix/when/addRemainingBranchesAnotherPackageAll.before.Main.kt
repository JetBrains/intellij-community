// "Add remaining branches with * import" "true"
// ERROR: 'when' expression must be exhaustive, add necessary 'RED', 'GREEN', 'BLUE' branches or 'else' branch instead

package u

import e.getOwnEnum

fun mainContext() {
    val ownLocal = getOwnEnum()
    <caret>when (ownLocal) {}
}