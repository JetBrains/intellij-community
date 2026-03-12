val numbers = mutableListOf(
    12, 7, 25, 42, 9,
    18, 33, 56, 71, 64,
    5, 29, 48, 90, 13,
    37, 22, 81, 44, 60
)

println("Numbers:")
println(numbers)

println("\n========== BASIC LOOPS ==========")

for (n in numbers) {
    if (n % 2 == 0) {
        println("Even number: $n")
    } else {
        println("Odd number: $n")
    }
}

println("\n========== INDEXED LOOP ==========")

for (i in numbers.indices) {
    val value = numbers[i]
    println("Index=$i value=$value doubled=${value * 2}")
}

println("\n========== MAP / FILTER ==========")

val processed = numbers
    .map {
        val result = it * 3
        println("Mapping $it -> $result")
        result
    }
    .filter {
        val keep = it % 5 != 0
        println("Filtering $it keep=$keep")
        keep
    }

println("Processed result:")
println(processed)

println("\n========== REDUCE ==========")

if (processed.isNotEmpty()) {
    val reduced = processed.reduce { acc, value ->
        println("Reduce step acc=$acc value=$value")
        acc + value
    }
    println("Reduced sum = $reduced")
}

println("\n========== FOLD ==========")

val folded = processed.fold(100) { acc, value ->
    println("Fold step acc=$acc value=$value")
    acc - value
}

println("Fold result = $folded")

println("\n========== GROUPING ==========")

val grouped = numbers.groupBy {
    when {
        it < 33 -> "LOW"
        it < 66 -> "MID"
        else -> "HIGH"
    }
}

println("Grouped numbers:")
for ((key, list) in grouped) {
    println("$key -> $list")
}

println("\n========== MAP OPERATIONS ==========")

val nameMap = mapOf(
    "Alice" to 28,
    "Bob" to 34,
    "Charlie" to 52,
    "Diana" to 23,
    "Eve" to 45
)

println("Name map:")
println(nameMap)

nameMap.forEach { (name, age) ->
    println("$name is $age years old")
}

val ageCategories = nameMap.mapValues { (_, age) ->
    when {
        age < 30 -> "Young"
        age < 45 -> "Adult"
        else -> "Senior"
    }
}

println("Age categories:")
println(ageCategories)

println("\n========== NESTED COLLECTIONS ==========")

val matrix = listOf(
    listOf(1, 4, 7, 2, 9),
    listOf(3, 8, 5, 6, 0),
    listOf(9, 2, 4, 1, 7),
    listOf(6, 3, 8, 5, 2),
    listOf(4, 7, 1, 9, 3)
)

println("Matrix:")
matrix.forEach { println(it) }

println("\nIterating matrix:")

for (row in matrix) {
    for (cell in row) {
        print("$cell ")
    }
    println()
}

println("\n========== FLATMAP ==========")

val flattened = matrix.flatMap { row ->
    println("Flatten row: $row")
    row
}

println("Flattened:")
println(flattened)

println("\n========== BUILD LIST ==========")

val built = buildList {
    for (i in 1..10) {
        val v = i * i
        println("Adding square $v")
        add(v)
    }
}

println("Built list:")
println(built)

println("\n========== SORTING ==========")

val sorted = numbers.sorted()
val sortedDesc = numbers.sortedDescending()

println("Sorted ascending: $sorted")
println("Sorted descending: $sortedDesc")

println("\n========== WINDOWED ==========")

val windows = numbers.windowed(3, step = 2)

windows.forEachIndexed { index, window ->
    println("Window $index -> $window sum=${window.sum()}")
}

println("\n========== DONE ==========")