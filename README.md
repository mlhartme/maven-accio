# Maven Summon

Maven Summon is a library to resolve artifacts from Maven repositories and load Maven poms files. It provides a 
simple api for use in Java code, it does not include a command line interface.

Summon is kind of a stripped-down Maven for "read-only" functionality, it does not execute any build phases, 
plugins or extensions. Technically, it uses the original Maven Libraries to resolve artifacts and load poms, 
but omits the execution (or actively blocks - see security below) stuff.

## Example

Load a pom file like this:

    ...

    import org.apache.maven.project.MavenProject;
    import net.oneandone.maven.summon.api.Maven;

    ... 

    Maven maven = Maven.create();
    MavenProject pom = maven.loadPom("pom.xml");
    System.out.println("artifactId: " + pom.getArtifactId();


## Security

Loading a poms with Maven has two security problems that I am aware of:
* extensions in a pom can inject arbitrary code
* repositories defined in a pom can inject code by overriding Maven components

To mitigate this, Summon provides an ExtensionBlocker and a PomRepositoryBlocker. They can also be use
separately as a core extension.

Additionally, Maven instantiates extension Objects in [DefaulMaven.getLifecycleParticipants()](https://github.com/apache/maven/blob/21122926829f1ead511c958d89bd2f672198ae9f/maven-core/src/main/java/org/apache/maven/DefaultMaven.java#L327C5-L327C5).
Summon does not run this code.

There are other [extensions points](https://maven.apache.org/examples/maven-3-lifecycle-extensions.html):
* EventSpyDispatcher/EventSpy, used for plugin validation in Maven 3.9.3
* ExecutionEventCatapult/ExecutionListener, used for console/log output
but they do not load code like AbstractMavenLifecycleParticipant.

## Core extension

In addition to the above use case, Summon can be used as a core extension and configured via properties.
Use this to block extensions from being loaded and plugin repositories being configured in projects.


## Name

https://harrypotter.fandom.com/wiki/Summoning_Charm