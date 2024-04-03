package a

// this import should be removed by the import optimizer even though there are no available usages
import b.Y

fun main() {
    Y().foo()
}