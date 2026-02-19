// NAME: Mapper

// SIBLING:
fun <T> mapOnce(v: T, t: <selection>(T) -> T</selection>): T {
    return t(v)
}



// IGNORE_K1