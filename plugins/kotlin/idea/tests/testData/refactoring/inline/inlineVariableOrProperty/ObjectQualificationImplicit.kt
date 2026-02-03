package f

import f.StandAloneContext.stopKoin

object StandAloneContext {
    val closeKoin = 1

    val stopKoin = closeKoin
}


fun koin() {
    val i = stop<caret>Koin
}
// IGNORE_K1