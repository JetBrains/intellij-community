# A bench run sees no account, no Java preferences and no common data of the host

## Context

A bench run gives the IDE its own config, system and plugins directories. Three stores of the host can still reach
the IDE:

- `PathManager.getCommonDataPath()` takes `<user.home>/Library/Application Support/JetBrains` on macOS. The Air mobile
  control reads its pins there.
- `JBAManager` keeps the JetBrains Account login in the Java preferences, node `jetbrains/jetprofile`. On macOS the
  JDK keeps the preferences of every JVM of a user in one store. `user.home` does not move it.
- The password safe of the IDE uses the keychain of the host by default on macOS.

A UI lane starts the IDE in a guest with a fresh home and no account. Without isolation, the bench and the lane measure
two different start-ups of the Air plugin. The README section [The bench](../../../../README.md#the-bench) lists the
verbs.

## Decisions

1. **The sandbox has a home.** Each JVM of a run gets `-Duser.home=<sandbox>/home`. This covers the headless run of the
   template, an open request and a quit too. The template holds the empty directory, so a prime run and a twin get a
   home of their own.
2. **The Java preferences are in the memory of the JVM.** The target
   [`bench-preferences`](../../../../bench-preferences/) builds `InMemoryPreferencesFactory`, with an empty user root
   and an empty system root. The stage copies it into the generation as `bench-preferences.jar`, and the generation
   key covers it. The layout of a generation is `bench-generation-3`. Each JVM gets
   `-Djava.util.prefs.PreferencesFactory=com.intellij.tools.vm.bench.InMemoryPreferencesFactory` and
   `-Xbootclasspath/a:<generation>/bench-preferences.jar`. `--generation` refuses a generation without the jar.
3. **The passwords are in the memory of the IDE.** The template holds `options/security.xml` with the provider
   `MEMORY_ONLY` of the password safe, as the IDE Starter writes it on Linux.
4. **A cargo-sync run keeps the home of the host.** The Rust plugin finds the toolchain in `~/.cargo` below
   `user.home`. The run gets the other two settings, and a note of the run says so.
5. **The measurement model is `integration-test v3`.** `compare` notes a session of an earlier version against a
   session of v3.

## Alternatives rejected

- **Only `user.home`.** The account stays signed in, because the Java preferences do not depend on `user.home`.
- **A switch of the platform, such as a property of `Prefs` or of `JBAManager`.** A switch changes the code of the
  product that the bench measures.
- **A second macOS user for the IDE.** Each run then needs a login session of another user, and other permissions on
  its sandbox and generation.

## Consequences

- A median of Air before this model and a median after it do not compare for the Air mobile control. The same applies
  to any code that reads the account.
- A generation of an earlier layout stages again at the next session. `--generation` with an old digest is a refusal.
- An IDE of a run starts without the JDKs of `~/Library/Java/JavaVirtualMachines` and without `~/.m2` and `~/.gradle`
  of the host. The arms of the bench need none of them.
- The find-in-files arm with `--in-place` runs accio from `PATH`. Accio takes its index from the `HOME` of the
  environment, which the run keeps.
- The file store of the JDK, `FileSystemPreferencesFactory`, is no option. It fails on macOS with
  `UnsatisfiedLinkError`, because the JBR has no native part of it.
