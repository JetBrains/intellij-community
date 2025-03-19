package f

import f.StandAloneContext.stopKoin

object StandAloneContext {
    val closeKoin = 1

    val stopKoin = this.closeKoin
}


fun koin() {
    val i = stop<caret>Koin
}

// IGNORE_K1