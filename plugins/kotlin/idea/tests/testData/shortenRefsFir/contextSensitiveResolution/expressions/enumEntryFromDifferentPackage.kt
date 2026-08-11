// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package test

import other.Direction

fun take(d: Direction) {}

fun usage() {
    take(<selection>Direction.NORTH</selection>)
}
