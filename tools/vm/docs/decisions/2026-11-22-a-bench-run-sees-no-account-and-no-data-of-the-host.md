---
topic: testing
---

# A bench run sees no account and no data of the host

Date: 2026-11-22

## Status

Accepted. The verbs are in [the vm-lane README](../../README.md), section **The bench**, and the grammar of each is its
`--help`. The measurement model of `session.json` is `integration-test v3` from this record on.

## Context

A run of `vm bench` gave the IDE its own config, system and plugins directories, and nothing else. Three stores of
the host still reached the IDE:

- **The common data.** `PathManager.getCommonDataPath()` takes `<user.home>/Library/Application Support/JetBrains` on
  macOS. No property of the platform sets it. The Air mobile control reads its pins there, such as
  `air-mobile/trusted-devices.json`, and a run wrote into that directory of the host.
- **The Java preferences.** `JBAManager` keeps the JetBrains Account login in the Java preferences of the user, in the
  node `jetbrains/jetprofile`. On macOS the JDK keeps these preferences of every JVM of a user in one store of the
  user. `user.home` does not move it, and neither does `CFFIXED_USER_HOME`. The file store of the JDK,
  `FileSystemPreferencesFactory`, fails on macOS with `UnsatisfiedLinkError`, because the JBR has no native part of
  it.
- **The keychain.** The password safe of the IDE uses the keychain of the host by default on macOS.

So each run started signed in to the account of the host. In session `develar-bench-8d6295e5`, `plugin-classes.txt`
of `empty-editor-run-03` held `AirMobileControlFlag$Enabled`, and `idea.log` held `#JBAManager - Authentication
required`. A UI lane starts the IDE in a guest with a fresh home, so it has no account. The bench and the lane then
measured two different start-ups of the Air plugin.

## Decision

**The IDE of a run sees no account, no Java preferences and no common data of the host, as a lane with a fresh home
does.**

1. **The sandbox has a home.** Each JVM of a run, of the headless run of the template, of an open request and of a
   quit gets `-Duser.home=<sandbox>/home`. The template holds the empty directory, so a prime run and a twin get a
   home of their own.
2. **The Java preferences are in the memory of the JVM.** `@community//tools/vm/bench-preferences` builds
   `InMemoryPreferencesFactory`, a factory with an empty user root and an empty system root. A session builds it with
   the distribution, and the stage copies it into the generation as `bench-preferences.jar`. The key of the generation
   covers it, and the layout of a generation is `bench-generation-3`. Each JVM gets
   `-Djava.util.prefs.PreferencesFactory=com.intellij.tools.vm.bench.InMemoryPreferencesFactory` and
   `-Xbootclasspath/a:<generation>/bench-preferences.jar`. `--generation` refuses a generation without the jar.
3. **The passwords are in the memory of the IDE.** The template holds `options/security.xml` with the provider
   `MEMORY_ONLY` of the password safe, as the IDE Starter writes it on Linux.
4. **A cargo-sync run keeps the home of the host.** The Rust plugin finds the toolchain in `~/.cargo` below
   `user.home` (`CargoToolchainFlavor`). The run gets the other two settings, and a note of the run says so.
5. **The measurement model is `integration-test v3`.** `compare` notes a session of v2 against a session of v3.

## Measurements

One run of `vm.cmd bench project markdown --arm empty-editor --runs 1 --hold 15s` before and after the change, on the
macOS host of the bench, at a load of 118 to 190 on 18 CPUs. The class counts come from `bench classes <session>
--arm empty-editor --plugin com.intellij.air --at quit`. The load changes the times, not the counts.

| | Before (`develar-bench-b7502126`) | After (`develar-bench-3ca02e55`) |
|---|---:|---:|
| classes of `com.intellij.air` | 5308 | 5286 |
| `intellij.air.shared.mobile` | 63 | 4 |
| `intellij.air.backend.mobile` | 23 | 2 |
| `intellij.air.frontend.mobile` | 11 | 2 |
| `AirMobileControlFlag` lines in `plugin-classes.txt` | 2 | 0 |
| `#JBAManager` lines in `idea.log` | an `Authentication required` line | none |

- Both sessions built the distribution from the same tree. Only the controller and the preferences jar differed.
- After the change, the IDE wrote `air/launch`, `Daemon` and `discovery` below the home of its sandbox. Before it,
  the run changed `air/launch` and `PrivacyPolicy/aiEua.cached` in the common data of the host.
- Around the run after the change, only `JetBrainsLight2027.1` changed in the common data of the host. That is the
  config of the IDE of the user, which ran at the same time. The store of the Java preferences of the host kept its
  time of change.

## Alternatives considered

- **Only `user.home`.** Rejected. A peer session ran with `--property user.home=<empty directory>`, and the account
  stayed signed in, because the Java preferences do not depend on `user.home`.
- **A switch of the platform, such as a property of `Prefs` or of `JBAManager`.** Rejected. A switch changes the code
  of the product that the bench measures, and the platform has no such switch now.
- **A second user of macOS for the IDE.** Rejected. Each run would need a login session of another user, and its
  sandbox and its generation would need other permissions.
- **The file store of the JDK.** Not possible. It fails on macOS, see the context.

## Consequences

- A median of Air before this record and a median after it are not comparable for the Air mobile control and for any
  code that reads the account. `compare` notes the two models.
- A generation of an earlier layout stages again at the next session. `--generation` with an old digest is a refusal.
- An IDE of a run starts without the JDKs of `~/Library/Java/JavaVirtualMachines` and without `~/.m2` and
  `~/.gradle` of the host. The arms of the bench need none of them. The find-in-files arm with `--in-place` runs accio
  from `PATH`, and accio takes its index from the `HOME` of the environment, which the run keeps.
