Test runners wraps python framework and provides teamcity protocol.
They are are distributed separately.
Copy latest version from https://github.com/JetBrains/teamcity-messages.git/teamcity
See https://confluence.jetbrains.com/display/~link/PyCharm+test+runners+protocol

When updating, place "twisted.plugins" one level up because "twisted" should be in sys.path.
See  twisted/plugins/README.txt