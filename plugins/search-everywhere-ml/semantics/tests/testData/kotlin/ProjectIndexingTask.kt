private const val MB = 1024 * 1024

class ProjectIndexingTask(project: String) {
  companion object {
    private const val taskTitle = "Collecting project files..."

    fun startIndexing(timeout: Int) {
      Thread.sleep(timeout.toLong())
    }
  }

  private fun getUsedMemory(): Long {
    val runtime = Runtime.getRuntime()
    return (runtime.totalMemory() - runtime.freeMemory()) / MB
  }
}
