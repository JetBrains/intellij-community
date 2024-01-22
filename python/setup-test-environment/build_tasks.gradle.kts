import org.apache.tools.ant.taskdefs.condition.Os

tasks.register<Exec>("kill_python_processes") {
  onlyIf { Os.isFamily(Os.FAMILY_WINDOWS) }

  // TODO: looks ugly, how can it be improved?
  commandLine("powershell", """"Get-Process | where {${'$'}_.Name -ieq \"python\"} | Stop-Process"""")
}

tasks.register<Delete>("clean_all") {
  dependsOn("kill_python_processes")
  mustRunAfter("kill_python_processes")

  delete(project.layout.buildDirectory)
}

tasks.register("build_all") {
  mustRunAfter("clean_all")
  dependsOn(tasks.matching { it.name.startsWith("setup_") }, "clean_all")
}
