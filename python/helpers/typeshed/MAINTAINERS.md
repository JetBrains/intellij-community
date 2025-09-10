At present the active maintainers are (alphabetically):

* Rebecca Chen (@rchen152)
* Jukka Lehtosalo (@JukkaL)
* Ivan Levkivskyi (@ilevkivskyi)
* Sebastian Rittau (@srittau)
* Guido van Rossum (@gvanrossum)
* Brian Schubert (@brianschubert)
* Shantanu (@hauntsaninja)
* Nikita Sobolev (@sobolevn)
* Samuel Therrien (@Avasam)
* Aku Viljanen (@Akuli)
* Alex Waygood (@AlexWaygood)
* Jelle Zijlstra (@JelleZijlstra)

Former maintainers include:

* David Fisher (@ddfisher)
* Matthias Kramm (@matthiaskramm)
* Łukasz Langa (@ambv)
* Greg Price (@gnprice)
* Rune Tynan (@CraftSpider)

For security reasons, maintainers who haven't been active for twelve months
(no PR reviews or merges, no opened PRs, no significant participation in
issues or typing-related discussion) will have their access rights removed.
They will also be moved to the "former maintainers" section here.

Former maintainers who want their access rights restored should open
an issue or mail one of the active maintainers.

## Maintainer guidelines

The process for preparing and submitting changes as outlined
in the [CONTRIBUTING document](./CONTRIBUTING.md) also applies to
maintainers.  This ensures high quality contributions and keeps
everybody on the same page.  Do not make direct pushes to the repository.

### Reviewing and merging pull requests

When reviewing pull requests, follow these guidelines:

* Typing is hard. Try to be helpful and explain issues with the PR,
  especially to new contributors.
* When reviewing auto-generated stubs, just scan for red flags and obvious
  errors. Leave possible manual improvements for separate PRs.
* When reviewing large, hand-crafted PRs, you only need to look for red flags
  and general issues, and do a few spot checks.
* Review smaller, hand-crafted PRs thoroughly.

When merging pull requests, follow these guidelines:

* Always wait for tests to pass before merging PRs.
* Use "[Squash and merge](https://github.com/blog/2141-squash-your-commits)" to merge PRs.
* Make sure the commit message is meaningful. For example, remove irrelevant
  intermediate commit messages.
* The commit message for third-party stubs is used to generate the changelog.
  It should be valid Markdown, be comprehensive, read like a changelog entry,
  and assume that the reader has no access to the diff.
* Delete branches for merged PRs (by maintainers pushing to the main repo).

### Marking PRs as "deferred"

*See also the [guidelines in the CONTRIBUTING file](./CONTRIBUTING.md#marking-prs-as-deferred).*

PRs should only be marked as "deferred" if there is a clear path towards getting
the blocking issue resolved within a reasonable time frame. If a PR depends on
a more amorphous change, such as a type system change that has not yet reached
the PEP stage, it should instead be closed.

Maintainers who add the "deferred" label should state clearly what exactly the
blocker is, usually with a link to an open issue in another project.

### Closing stale PRs

*See also the [guidelines in the CONTRIBUTING file](./CONTRIBUTING.md#closing-stale-prs).*

We want to maintain a welcoming atmosphere for contributors, so use a friendly
message when closing the PR. Example message:

    Thanks for contributing! I'm closing this PR for now, because it still <fails some tests OR has unresolved review feedback OR has a merge conflict> after three months of inactivity. If you are still interested, please feel free to open a new PR (or ping us to reopen this one).

### Closing PRs for future standard library changes

*See also the [guidelines in the CONTRIBUTING file](./CONTRIBUTING.md#standard-library-stubs).*

When rejecting a PR for a change for a future Python version, use a message
like:

    Thanks for contributing! Unfortunately, [as outlined in our CONTRIBUTING document](https://github.com/python/typeshed/blob/main/CONTRIBUTING.md#standard-library-stubs) we only accept pull requests to the standard library for future Python versions after the first beta version has been released. This is in part to prevent churn in the stubs, and in part because the testing infrastructure for the future version is not yet in place. Please feel free to open a new PR when the first beta version has been released. Alternatively, if this PR is still relevant, you can leave a comment here to reopen it.
