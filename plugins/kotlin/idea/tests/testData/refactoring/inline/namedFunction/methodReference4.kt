fun bound(p: Int) = p + 1
class Bounds { fun bound(p: Int) = p + 1 }
fun acceptBound(p: Int, f: (Int) -> Int) = f(p)
fun boundContext1() = acceptBound(0, ::bound)
fun boundContext2() = acceptBound(0, Bounds()::bo<caret>und)