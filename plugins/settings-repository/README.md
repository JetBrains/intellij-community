# Installation

Available for all IntelliJ Platform based products (build number greater than 140.2). Settings | Plugins | Browse repositories -> type in Settings Repository.

Don't try to install plugin from disk — otherwise you have to be aware of compatibility.

# Configuration

Use File -> Settings Repository… to configure.

Specify URL of upstream Git repository. File URL is supported, you will be prompted to init repository if specified path is not exists or repository is not created.
[GitHub](www.github.com) could be used to store settings.

## Authentication
On first sync you will be prompted to specify username/password. In case of GitHub strongly recommended to use an [access token](https://help.github.com/articles/creating-an-access-token-for-command-line-use) (leave password empty if you use token instead of username). Bitbucket [doesn't support tokens](https://bitbucket.org/site/master/issue/7735).

If you still want to use username/password instead of access token or your Git hosting provider doesn't support it, recommended to configure [git credentials helper](https://help.github.com/articles/caching-your-github-password-in-git).

OS X Keychain is supported. It means that your credentials could be shared between all IntelliJ Platform based products (you will be prompted to grant access if origin application differs from requestor application (credentials were saved in IntelliJ IDEA, but requested from WebStorm).

## How to report issues
Use [JetBrains YouTrack](https://youtrack.jetbrains.com) — project IntelliJ IDEA, subsystem IDE Configuration, assignee Vladimir Krivosheev ([issue template](https://youtrack.jetbrains.com/newIssue?project=IDEA&clearDraft=true&c=Assignee+develar&c=Subsystem+IDE+Configuration)).