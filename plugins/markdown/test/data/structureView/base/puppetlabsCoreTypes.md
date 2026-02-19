Core Types Cheat Sheet
=====


[notify]: https://docs.puppetlabs.com/references/latest/type.html#notify
[file]: https://docs.puppetlabs.com/references/latest/type.html#file
[package]: https://docs.puppetlabs.com/references/latest/type.html#package
[service]: https://docs.puppetlabs.com/references/latest/type.html#service
[exec]: https://docs.puppetlabs.com/references/latest/type.html#exec
[cron]: https://docs.puppetlabs.com/references/latest/type.html#cron
[user]: https://docs.puppetlabs.com/references/latest/type.html#user
[group]: https://docs.puppetlabs.com/references/latest/type.html#group
[other]: https://docs.puppetlabs.com/references/latest/type.html

The Trifecta
-----

Package/file/service: Learn it, live it, love it. Even if this is the only Puppet you know, you can still get a whole lot done.

    package { 'openssh-server':
      ensure => installed,
    }

    file { '/etc/ssh/sshd_config':
      source  => 'puppet:///modules/sshd/sshd_config',
      owner   => 'root',
      group   => 'root',
      mode    => '0640',
      notify  => Service['sshd'], # sshd restarts whenever you edit this file.
      require => Package['openssh-server'],
    }

    service { 'sshd':
      ensure     => running,
      enable     => true,
    }

**INSERT PICTURE OF DEPENDENCY GRAPH HERE**

### [file][]

Manages files, directories, and symlinks.

#### Important Attributes

* [`ensure`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-ensure) -- Whether the file should exist, and what it should be. Allowed values:
    * `file` (a normal file)
    * `directory` (a directory)
    * `link` (a symlink)
    * `present` (anything)
    * `absent`
* [`path`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-path) -- The full path to the file on disk; **defaults to title.**
* [`owner`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-owner) -- By name or UID.
* [`group`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-group) -- By name or GID.
* [`mode`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-mode) -- Must be specified exactly. Does the right thing for directories.

#### For Normal Files

* [`source`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-source) -- Where to download contents for the file. Usually a `puppet:///` URL.
* [`content`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-content) -- The file's desired contents, as a string. Most useful when paired with [templates](https://docs.puppetlabs.com/guides/templating.html), but you can also use the output of the [file function](https://docs.puppetlabs.com/references/latest/function.html#file).

#### For Directories

* [`source`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-source) -- Where to download contents for the directory, when `recurse => true`.
* [`recurse`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-recurse) -- Whether to recursively manage files in the directory.
* [`purge`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-purge) -- Whether unmanaged files in the directory should be deleted, when `recurse => true`.

#### For Symlinks

* [`target`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-target) -- The symlink target. (Required when `ensure => link`.)

#### Other Notable Attributes

[`backup`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-backup), [`checksum`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-checksum), [`force`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-force), [`ignore`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-ignore), [`links`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-links), [`recurselimit`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-recurselimit), [`replace`](https://docs.puppetlabs.com/references/latest/type.html#file-attribute-replace)

### [package][]

Manages software packages. Some platforms have better package tools than others, so you'll have to do some research on yours; click the link and check out the type reference for more detailed info.

#### Important Attributes

* [`name`](https://docs.puppetlabs.com/references/latest/type.html#package-attribute-name) -- The name of the package, as known to your packaging system; **defaults to title.**
* [`ensure`](https://docs.puppetlabs.com/references/latest/type.html#package-attribute-ensure) -- Whether the package should be installed, and what version to use. Allowed values:
    * `present`
    * `latest` (implies `present`)
    * any version string (implies `present`)
    * `absent`
    * `purged` (Potentially dangerous. Ensures absent, then zaps configuration files and dependencies, including those that other packages depend on. Provider-dependent.)
* [`source`](https://docs.puppetlabs.com/references/latest/type.html#package-attribute-source) -- Where to obtain the package, if your system's packaging tools don't use a repository.
* [`provider`](https://docs.puppetlabs.com/references/latest/type.html#package-attribute-provider) -- Which packaging system to use (e.g. Yum vs. Rubygems), if a system has more than one available.

### [service][]

Manages services running on the node. Like with packages, some platforms have better tools than others, so read up.

You can make services restart whenever a file changes, with the `subscribe` or `notify` metaparameters. For more info, [read about relationships.](https://docs.puppetlabs.com/puppet/latest/reference/lang_relationships.html)

#### Important Attributes

* [`name`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-name) -- The name of the service to run; **defaults to title.**
* [`ensure`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-ensure) -- The desired status of the service. Allowed values:
    * `running` (or `true`)
    * `stopped` (or `false`)
* [`enable`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-enable) -- Whether the service should start on boot. Doesn't work on all systems.
* [`hasrestart`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-hasrestart) -- Whether to use the init script's restart command instead of stop+start. Defaults to false.
* [`hasstatus`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-hasstatus) -- Whether to use the init script's status command. Defaults to true.

#### Other Notable Attributes

If a service has a bad init script, you can work around it and manage almost anything using the [`status`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-status), [`start`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-start), [`stop`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-stop), [`restart`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-restart), [`pattern`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-pattern), and [`binary`](https://docs.puppetlabs.com/references/latest/type.html#service-attribute-binary) attributes.


Hello World
-----

### <caret>[notify][]

Logs an arbitrary message, at the `notice` log level. This appears in the POSIX syslog or Windows Event Log on the Puppet agent node and is also logged in reports.

    notify { "This message is getting logged on the agent node.": }

#### Important Attributes

* [`message`](https://docs.puppetlabs.com/references/latest/type.html#notify-attribute-message) -- **Defaults to title.**



Grab Bag
-----

### [exec][]

Executes an arbitrary command on the agent node. When using execs, you must either make sure the command can be safely run multiple times, or specify that it should only run under certain conditions.

#### Important Attributes

* [`command`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-command) -- The command to run; **defaults to title.** If this isn't a fully-qualified path, use the `path` attribute.
* [`path`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-path) -- Where to look for executables, as a colon-separated list or an array.
* [`returns`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-returns) -- Which exit codes indicate success. Defaults to `0`.
* [`environment`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-environment) -- An array of environment variables to set (e.g. `['MYVAR=somevalue', 'OTHERVAR=othervalue']`).

#### Attributes to Limit When a Command Should Run

* [`creates`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-creates) -- A file to look for before running the command. The command only runs if the file doesn’t exist.
* [`refreshonly`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-refreshonly) -- If `true`, the command only run if a resource it subscribes to (or a resource which notifies it) has changed.
* [`onlyif`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-onlyif) -- A command or array of commands; if any have a non-zero return value, the command won't run.
* [`unless`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-unless) -- The opposite of onlyif.

#### Other Notable Attributes:

[`cwd`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-cwd), [`group`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-group), [`logoutput`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-logoutput), , [`timeout`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-timeout), [`tries`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-tries), [`try_sleep`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-try_sleep), [`user`](https://docs.puppetlabs.com/references/latest/type.html#exec-attribute-user).

### [cron][]

Manages cron jobs. Largely self-explanatory. On Windows, you should use [`scheduled_task`](https://docs.puppetlabs.com/references/latest/type.html#scheduledtask) instead.

    cron { 'logrotate':
      command => "/usr/sbin/logrotate",
      user    => "root",
      hour    => 2,
      minute  => 0,
    }

#### Important Attributes

* [`command`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-command) -- The command to execute.
* [`ensure`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-ensure) -- Whether the job should exist.
    * present
    * absent
* [`hour`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-hour), [`minute`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-minute), [`month`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-month), [`monthday`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-monthday), and [`weekday`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-weekday) -- The timing of the cron job.

#### Other Notable Attributes

[`environment`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-environment), [`name`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-name), [`special`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-special), [`target`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-target), [`user`](https://docs.puppetlabs.com/references/latest/type.html#cron-attribute-user).

### [user][]

Manages user accounts; mostly used for system users.

    user { "jane":
        ensure     => present,
        uid        => '507',
        gid        => 'admin',
        shell      => '/bin/zsh',
        home       => '/home/jane',
        managehome => true,
    }

#### Important Attributes

* [`name`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-name) -- The name of the user; **defaults to title.**
* [`ensure`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-ensure) -- Whether the user should exist. Allowed values:
    * `present`
    * `absent`
    * `role`
* [`uid`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-uid) -- The user ID. Must be specified numerically; chosen automatically if omitted. Read-only on Windows.
* [`gid`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-gid) -- The user’s primary group. Can be specified numerically or by name. (Not used on Windows; use `groups` instead.)
* [`groups`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-groups) -- An array of other groups to which the user belongs. (Don't include the group specified as the `gid`.)
* [`home`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-home) -- The user's home directory.
* [`managehome`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-managehome) -- Whether to manage the home directory when managing the user; if you don't set this to true, you'll need to create the user's home directory manually.
* [`shell`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-shell) -- The user's login shell.

#### Other Notable Attributes

[`comment`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-comment), [`expiry`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-expiry), [`membership`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-membership), [`password`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-password), [`password_max_age`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-password_max_age), [`password_min_age`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-password_min_age), [`purge_ssh_keys`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-purge_ssh_keys), [`salt`](https://docs.puppetlabs.com/references/latest/type.html#user-attribute-salt).

### [group][]

Manages groups.

#### Important Attributes

* [`name`](https://docs.puppetlabs.com/references/latest/type.html#group-attribute-name) -- The name of the group; **defaults to title.**
* [`ensure`](https://docs.puppetlabs.com/references/latest/type.html#group-attribute-ensure) -- Whether the group should exist. Allowed values:
    * `present`
    * `absent`
* [`gid`](https://docs.puppetlabs.com/references/latest/type.html#group-attribute-gid) -- The group ID; must be specified numerically, and is chosen automatically if omitted. Read-only on Windows.
* [`members`](https://docs.puppetlabs.com/references/latest/type.html#group-attribute-members) -- Users and groups that should be members of the group. Only applicable to certain operating systems; see the full type reference for details.

Everything Else
-----

You are ready. [Go check the resource type reference][other].
