# Maven Accio

Maven Accio can resolve artifacts from Maven repositories and load Maven poms files. It provides a simple api 
for use in Java code, it does not include a command line interface.

Accio is kind of a stripped-down Maven for "read-only" functionality, it does not do execute any builds, plugins or extensions,
Technically, it uses the original Maven Libraries to resolve artifacts and load poms, but omitted the execution stuff.

Loading a poms with Maven has a security problem because extensions can inject code via extensions.
Loading poms with Accio is considered save because it does intentionally *not* execute extensions -
neither core extensions, nor plugin extensions, nor build extensions.

Technically, Maven loaded extensions code in [DefaulMaven.getLifecycleParticipants()](https://github.com/apache/maven/blob/21122926829f1ead511c958d89bd2f672198ae9f/maven-core/src/main/java/org/apache/maven/DefaultMaven.java#L327C5-L327C5)
by instantiating AbstractMavenLifecycleParticipant. Accio does not do this.

There are other [extensions points](https://maven.apache.org/examples/maven-3-lifecycle-extensions.html):
* EventSpyDispatcher/EventSpy, used for plugin validation in Maven 3.9.3
* ExecutionEventCatapult/ExecutionListener, used for console/log output
but they do not load code like AbstractMavenLifecycleParticipant.

## More class loading in Maven

Maven manages class loading with Plexus ClassWorlds, new code is added via ClassRealms, and a ClassRealmManager controls all this. 
This manager has methods to create project, plugin, and extension realms. ProjectRealm is a composite of the contained extension realms. 
Plugin realms are used when invoking plugin. 

When loading a pom, the project realm and thus all plugin realms are created, which enabled extensions to override components used
by Maven and thus inject code. Accio provides a BlockingClassRealmManager to avoid this.

## Name

https://harrypotter.fandom.com/wiki/Summoning_Charm