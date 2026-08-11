# Maven Sync Process: High level overview

Sync is process of synchronizing IDEA project model with a maven project model, including directories, modules, and their dependencies.

It consists of two big stages - Static Sync and Dynamic sync

## Static Sync

entry in `org.jetbrains.idea.maven.project.preimport.MavenProjectStaticImporter#syncStatic`

Static sync reads Maven files as xml only. It performs basic interpolation only. It **MUST NOT** access network, Maven server, daemons.
It also **MUST NOT** evaluate user code.
Static sync is used on very first project opening to reduce creation time of Project model. Static sync could return not complete or even partially incorrect project model, for the sake of performance.

## Dynamic Sync
Includes any code execution, dependencies resolution and download, build tools invocation.
Dynamic sync consists of several stages:
### Read
On this stage the project model is collected and read. We read the project model without resolution. On this stage we collect Workspace Map for maven.

### Resolve
On this stage dependencies are collected, resolved and downloaded.

### Importing Workspace Model
Maven project model (which is represented in org.jetbrains.idea.maven.project.MavenProject) converted into Workspace Model entities and commited.
Also, MavenWorkspaceConfigurator instances are running.

### Plugins resolution and Sources and Javadocs downloading
On this stage we download plugin's artifacts and download Sources and Javadocs. This process goes in parallel with Importing Workspace Model in background

# What to change, and where
- Create new MavenWorkspaceConfigurator to support integration with maven plugin or library, if you need to tweak IDEA for the project
- 