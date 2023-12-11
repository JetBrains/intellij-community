object BranchConfiguration {
  const val Branch = "main"

  val BranchSpec = listOf(
    "+:refs/heads/*",
    "-:refs/heads/2*"
  ).joinToString(separator = "\n")

  const val IdSuffix = "Main"
}
