#Maven Indices.

## Why do they needed

1. Completion dependencies im pom.xml and gradle files
2. Highlighting im pom.xml and gradle files
3. Search for class functionality: Find artifact containing specific class


## Types of indices:
Actually, there are two types of indices of maven repository.
1. GAV(GroupId-ArtifactId-Version) for maven artifacts
2. Class search indices


## Lifeflow
All indices are created inside MavenSystemIndicesManager - application level. 
Also, this class is responsible to update, clean, and remove unused indices from memory

All interaction with classes should happen using MavenIndicesManager  for local gav and MavenLuceneIndexer

#### Lucene (class search)

When searching for class it is responsibility of calling site to correctly provide maven repositories to search in and ensure that indices are up to date
No implicit update for lucene indices happen in background. It is responsibility of calling site to ensure that indices are updated (_**TODO**_: how?) 



#### Local GAV (in memory)
MavenIndicesManager can return common Gav  index for specific project.
Update for GAV scheduled after index returned. Please note, that just after return index could be empty and return nothing, eventually in will be filled with data.
In unit tests index update scheduled only if `maven.skip.gav.update.in.unit.test.mode` set to false (true by default)