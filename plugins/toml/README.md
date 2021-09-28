# [TOML](https://github.com/toml-lang/toml) language support for IntelliJ IDEA based IDEs

[![download]][plugin]
[![version]][plugin]

 [download]: https://img.shields.io/jetbrains/plugin/d/8195-toml.svg
 [version]: https://img.shields.io/jetbrains/plugin/v/8195-toml.svg
 [plugin]: https://plugins.jetbrains.com/plugin/8195-toml

The plugin provides nothing but syntax highlighting at the moment.

## Installation

To install plugin open `Settings > Plugins > Browse repositories`, and search for TOML. 

## Contributing

See [Contributing](https://github.com/intellij-rust/intellij-rust/blob/master/CONTRIBUTING.md) in the IntelliJ-Rust plugin.

## Extending

It's possible to extend TOML support from other plugins: 
  
  * The PSI structure is expected remain backwards compatible.
  * `TomlKey` and `TomlValue` are `ContributedReferenceHost`s, so
    it's possible to inject references into them from third-party plugins,
    and provide completion and goto definition.
    
See https://github.com/intellij-rust/intellij-rust/pull/1982/ for an example.    
