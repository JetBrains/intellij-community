This Idea plugin integrates Idea with Subversion.

The plugin tries to be as tightly integrated with Idea as possible.

Like the CVS integration it uses Idea's project view to display the workspace.
Changed files are color coded in the project view and the editor just as in the CVS integration.

All files & directories in the project directory, that are in the subversion repository are tracked during
move and rename operations.

Files & directories created through Idea in the projects source path are automatically tracked.
You can configure whether the new files shall be added to the
subversion repository (Always, Ask, Never).

Additional directories to be tracked for newly created files can be entered in
the "Additional Path" list on the configuration page.

Files & directories in a subversion repository which are deleted through Idea are automatically tracked.
You can configure whether the deleted files shall also be deleted from the subversion repository (Always, Ask, Never).

Subversion operations can be trieggered from the Subversion menu (tools/Subversion) or from the context menu.

For exended subversion operations (administration, blaming, properties etc) you can start the SvnUp
dialog from the Subversion menu (tools/Subversion/SvnUp).
