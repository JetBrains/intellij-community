fun properties(key: String): String {
  return project.findProperty(key).toString()
}

group = properties("pluginGroup")
version = properties("pluginVersion")
