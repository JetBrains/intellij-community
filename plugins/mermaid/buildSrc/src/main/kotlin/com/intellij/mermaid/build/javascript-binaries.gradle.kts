val javascriptBinaries by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}

val javascriptImplementation by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
  extendsFrom(javascriptBinaries)
}

val javascriptBinariesSourceMaps by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}

val javascriptSourceMaps by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
  extendsFrom(javascriptBinariesSourceMaps)
}
