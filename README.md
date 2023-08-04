# Maven Summon

Maven Summon can resolve artifacts from Maven repositories and load Maven poms files. It provides a simple api 
for use in Java code, it does not include a command line interface.

Summon is kind of a stripped-down Maven for "read-only" functionality, it does not execute any build phases, 
plugins or extensions, Technically, it uses the original Maven Libraries to resolve artifacts and load poms, 
but omits the execution stuff.

## Security

Loading a poms with Maven has a security problem because poms can inject code via extensions.
Loading poms with Summon is considered save because it does load extensions -
neither core extensions, nor plugin extensions, nor build extensions.

Technically, Maven adds extensions to the class loader via ClassRealmManager.createExtensionRealm(). Summon wraps 
this with its ExtensionBlocker component to restrict class loading appropriately.

Additionally, Maven instantiates extension Objects in [DefaulMaven.getLifecycleParticipants()](https://github.com/apache/maven/blob/21122926829f1ead511c958d89bd2f672198ae9f/maven-core/src/main/java/org/apache/maven/DefaultMaven.java#L327C5-L327C5).
Summon does not run this code.

There are other [extensions points](https://maven.apache.org/examples/maven-3-lifecycle-extensions.html):
* EventSpyDispatcher/EventSpy, used for plugin validation in Maven 3.9.3
* ExecutionEventCatapult/ExecutionListener, used for console/log output
but they do not load code like AbstractMavenLifecycleParticipant.

## More class loading in Maven

Maven manages class loading with Plexus ClassWorlds, new code is added via ClassRealms, and a ClassRealmManager controls all this. 
This manager has methods to create project, plugin, and extension realms. ProjectRealm is a composite of the contained extension realms. 
Plugin realms are used when invoking plugin. 

When loading a pom, the project realm and thus all plugin realms are created, which enabled extensions to override components used
by Maven and thus inject code. Summon provides a BlockingClassRealmManager to avoid this.

## Core extension

In addition to the above use case, Summon can be used as a core extension and configured via properties.
Use this to block extensions from being loaded and plugin repositories being configured in projects.


## Name

https://harrypotter.fandom.com/wiki/Summoning_Charm