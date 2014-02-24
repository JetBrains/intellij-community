IntelliJ IDEA 8.0M1 - README

Thank you for downloading IntelliJ IDEA!

IntelliJ IDEA is a multi-platform Java IDE, which includes intelligent editor, rich-featured GUI designer,
visual debugger, javac/jikes/rmic compiler integration, refactoring, enhanced project navigation,
intelligent code inspection and analysis features, J2EE and JDK 1.5 support.


Contents:
=========
  BUILD.TXT           File containing the current build number
  KnownIssues.TXT     Known issues and workarounds
  README.TXT          This file
  bin/                Startup scripts for launching IntelliJ IDEA
  help/               Online help files
  jre/                Bundled JRE
  lib/                Library files
  license/            License files for IntelliJ IDEA and bundled software
  plugins/            Standard plugins
  redist/             Contains libraries that need to be redistributed with your application if certain IDEA features are used:
                      - forms_rt.jar should be distributed with your applications that use GUI forms with
                        the "GridLayoutManager (IntelliJ)" layout manager
                      - javac2.jar is an Ant task for building applications that use IntelliJ IDEA's UI Designer
                        bytecode generation or @NotNull assertions generation
                      - annotations.jar contains JDK 1.5 annotation classes for 'Constant Conditions & Exceptions' inspection tool.

  Install-Linux-tar.txt     Installation instructions for Linux (included in Linux installation package only)
  Install-Windows-zip.txt   Installation instructions for Windows zip-file  (included in Windows zip installation package only)

  USER_HOME/.IntelliJIdea80/

      config/         Configuration files (See INSTALLATION_HOME/bin/idea.properties to tweak location of the configs)
        codestyles/       User's code style settings
        colors/           User's colors and fonts settings
        fileTemplates/    Custom file templates
        filetypes/        Custom file types
        ideTalk/          ideTalk settings
        inspection/       Executable file and auxiliary data for running offline code inspections
        keymaps/          Contains files with custom keymaps
        migration/        API migration map
        options/          IDE options configuration files
        plugins/          Directory for custom plugins (it appears after the 1st plugin is installed)
        shelf/            Shelved changes (in standard .patch file format)
        templates/        Live templates, both built-in and custom
        tools/            External tools
        idea70.key        File containing your license key (editing not recommended)

      system/         Various IDEA internal caches including Local History data storage.
                      (See INSTALLATION_HOME/bin/idea.properties to tweak location of the caches).
                      Also log directory with IDEA log files is located there.


Installing IntelliJ IDEA
========================
  For Linux and other Generic Unix users:
  Please read the contents of the Install-Linux-tar.txt file.

  Installing on Mac OS:
  IntelliJ IDEA is distributed as a .dmg file. You only need to drag it to
  the destination folder, from which you can start the application.

Uninstalling IntelliJ IDEA
==========================
  If you installed IntelliJ IDEA with the help of Installation Wizard, then
  just run the INSTALLATION_HOME/bin/Uninstall.exe file

  To uninstall IntelliJ IDEA after manual installation, simply delete the
  contents of the IntelliJ IDEA home installation directory.


Licensing & pricing
==========================
  Licensing and pricing information can be found at http://www.jetbrains.com/idea/buy/index.html.


IntelliJ IDEA Overview
==========================
  For general info and facts on IntelliJ IDEA, you can refer to IntelliJ IDEA Info Kit at
  http://www.jetbrains.com/idea/documentation/product_info_kit.html.


IDEA Development Package
==========================
  Contains:
  - Source code for the OpenAPI classes;
  - Documentation (JavaDocs for the OpenAPI and a number of additional components);
  - Simple example plugins demonstrating usage of the OpenAPI;
  - Source code for plugins shipped with IDEA:
      * Plugin Development Kit
      * IDEtalk plugin
      * Images support plugin
      * Inspection Gadgets
      * Intention PowerPack
      * J2ME development support plugin
      * JavaScript support plugin
      * JavaScript inspections plugin
      * JavaScript Intention PowerPack
      * GWTStudio plugin
      * KlassMaster stacktrace unscramble plugin
      * StrutsAssistant plugin
      * StarTeam, Perforce, Subversion, Visual SourceSafe integration
      * Tomcat, Weblogic, WebSphere, Geronimo, JBoss, GlassFish, JSR45 integration

  Download page: http://www.jetbrains.com/idea/download/index.html

  Source code of additional open source plugins shipped with IntelliJ IDEA is available in the Subversion
  repository at:
  http://svn.jetbrains.org/idea/Trunk/bundled/


Using Plugins
=============
  IDEA now smartly integrates with the Community web-site which holds a repository of the third-party plugins to it.
  This integration, supplied with a convenient UI, helps you incorporate any available plugins without switching from
  IDEA settings dialog (Settings | Plugins).

  You can browse the plugins Web site, rate and comment on plugins at:
  http://plugins.intellij.net/


Home Page:
==========
  http://www.jetbrains.com


IntelliJ Technology Network
===========================
  http://www.intellij.net
  Early Access to new products, internal builds and patches, Bug/Features database, Forums/newsgroups


IntelliJ Community Site
=======================
  http://www.intellij.org
  The community-driven Wiki site dedicated to IntelliJ products and technologies.


Support
=======
  For technical support and assistance, you may find necessary information at the Support page
  (http://www.jetbrains.com/support/index.html) or contact us at support@jetbrains.com.


Bug Reporting:
==============
  Send emails to bugs@jetbrains.com


Contacting us:
==============
  sales@jetbrains.com       - Sales inquiries, billing, order processing questions
  support@jetbrains.com     - Technical support (all products)
  sales.us@jetbrains.com    - Sales inquiries in the United States
  support.us@jetbrains.com  - Technical support for US customers
  suggestions@jetbrains.com - Feature suggestions
  info@jetbrains.com        - Product inquiries


=============
You are encouraged to visit our IntelliJ IDEA web site at http://www.jetbrains.com/idea/
or to contact us via e-mail at feedback@jetbrains.com if you have any comments about
this release. In particular, we are very interested in any ease-of-use, user
interface suggestions that you may have. We will be posting regular updates
of our progress to our online forums and newsgroups.
=============
