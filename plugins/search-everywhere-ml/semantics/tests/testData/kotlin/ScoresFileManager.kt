class ScoresFileManager {
  fun handleScoresFile() {
    var count = 0
    repeat(10) {
      println("score: $count")
      count++
    }
  }

  companion object {
    fun clearFileWithScores(file: String) {
      println("cleared")
    }
  }
}
