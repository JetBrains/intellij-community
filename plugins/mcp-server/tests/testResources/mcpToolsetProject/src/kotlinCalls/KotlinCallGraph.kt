package kotlinCalls

class KotlinCallGraph {
  fun memberEntry() {
    topLeaf()
  }
}

fun topEntry() {
  topLeaf()
}

fun fileFacadeEntry() {
  topLeaf()
}

fun ambiguousKotlinEntry() {
  topLeaf()
}

fun topLeaf() {
}
