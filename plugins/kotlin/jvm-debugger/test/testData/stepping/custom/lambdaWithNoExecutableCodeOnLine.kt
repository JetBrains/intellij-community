package lambdaWithNoExecutableCodeOnLine

import java.util.function.Function

fun main() {
    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun {
        //Breakpoint!
        "1"
        //Breakpoint! (lambdaOrdinal = -1)
    }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun {}.applyFun { "some code" }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun { "1" }.applyFun {
        "2"
    }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun { "1" }.applyFun { ;
        "2"
    }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun { "1" }.applyFun { // no code, only comment
        "2"
    }

    "start".applyFun { "1"
        //Breakpoint! (lambdaOrdinal = -1)
    }.applyFun { "2" }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun { "1" }.applyFun { val useless = 1
        "2"
    }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun { "1" }.applyFun { "useless"
        "2"
    }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun { "1" }.applySam {
        "2"
    }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applySam { "1" }.applySam {
        "2"
    }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun { "1" }.applyFun {fun useless() { }
        "2"
    }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun {}.applyFun { "2" }

    //Breakpoint! (lambdaOrdinal = -1)
    "start".applyFun { "1" }.applyFun { s ->
        "$s 2"
    }
}

fun <T, P> T.applyFun(f: (T) -> P): P = f(this)

fun <T, P> T.applySam(f: Function<T, P>): P = f.apply(this)

// RESUME: 100