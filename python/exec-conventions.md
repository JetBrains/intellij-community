# PyCharm Exec — Conventions

Conventions for work in python exec modules. It includes at least all modules
under "PyCharm Exec Experts" ownership.

## Avoid instance checks as much as possible

When you should do some check on a sealed interface/class, avoid doing
`if (foo is Foo)`. You should always use exhaustive `when` or avoid
instance checks altogether.

In other cases you should prefer polymorphism rather than casting. One
example would be `FileSystem` which generally shouldn't be used like this:

```kotlin
(fileSystem as? TargetFileSystem).someTargetOnlyLogic()
```

## Prefer extension points for potentially extendable behavior (especially around tools)

When fixing/implementing something, avoid doing something like `isUv`/`isHatch`/etc. In
cases when behavior should be different - an extension point should be used. This also
applies for working with targets. In most cases there shouldn't be casting
`TargetEnvironmentRequest` to some WSL/Docker/etc specific request.

## Avoid using String (or its typealiases) for paths

You should always prefer using either NIO Path or `EelPath` for representing paths. The only
exception is paths on targets because they represent "local" paths there.

## Visibility of symbols should be as private as possible

Unless there's a specific intention to expose some API publicly, symbols should be as
private as possible. Mark them `internal` if needed in this module only, otherwise use
`@ApiStatus.Internal` and `@PyInternalExecApi`. You can skip using the second one only if
it's an internal API that is deliberately exposed to use by others in monorepo.

## Creating new module rules

When adding a new exec module:
* add `OWNERSHIP` for exec subteam (unless there's one somewhere up the tree)
* add `-Werror` to compiler options
* add opt-in for `PyInternalExecApi` to compiler options.

## Don't use warnings suppression

Avoid using `@Suppress` as much as possible, you should always try to find a better way
of implementing things. Warnings are there for a reason. Use it only if absolutely
necessary (or avoiding it creates a huge overhead).
