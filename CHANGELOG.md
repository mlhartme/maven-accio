## Changelog

### TODO

* dump compat dependency, i.e. get rid of LegacyRepositorySystem


### 4.0.0 (pending)

* loadPom now defauls to resolve dependencies and process plugins (previously, plugins were never processed)
* update Maven 3.3.9 libraries to 3.9.3
* dump Wagon dependency, Maven itself has replaced it
* switch from Aether 1.0.2.xx to Maven Artifact Resolver 1.9.13 (https://maven.apache.org/resolver/)
* switch from MavenSettingsBuilder to SettingsBuilder (fixes deprecation)


### 3.13.0 (2022-12-19)

* update lazy foss parent 1.0.2 to maven-parent 1.6.1: CAUTION: as a result, Java 17 is required now
* improved Maven.locateMaven:
  * check MAVEN_HOME first
  * properly deal with symlins
  * properly deal with libexec directory
  * renamed locateMavenConf
* Renamed branch `master` to `main`
* Update Sushi 3.1.2 to 3.3.0


### 3.12.2 (2016-12-05)

* Fixed NPE in Maven.repositoriesLegacy() (for profiles without activation).
* Update Sushi 3.1.0 to 3.1.2


### 3.12.1 (2016-06-10)

* Update Sushi dependencies from 3.0.0 to 3.1.0.


### 3.12.0 (2016-03-01)

* Update Maven dependencies from 3.3.3 to 3.3.9. This implies an update from Guava 14.0.1 to 19.0 and Wagon 2.9 to Wagon 2.10.
* Update Sushi dependencies from 2.8.x to 3.0.0.
* Compile for Java 8.


### 3.11.1 (2015-10-29)

* Update dependencies from Maven 3.2.5 to 3.3.3.
* Fix missing central repository when loading poms, because you might have to load a parent from Maven central.
* Rename remoteLoadRepositories to remoteLegacyRepositories and remoteResolveRepositories to remoteRepositories.


### 3.10.0 (2015-02-23)

* Rename remoteRepositoriesLegacy to remoteLoadRepositories and remoteRepositories to remoteResolveRepositories.
* Fix missing central repository when resolving artifacts.
* Fix activeByDefault when loading settings.


### 3.9.0 (2015-02-01)

* Updated aether 0.9 to 1.0.
* Updated maven 3.2.2 to 3.2.5.
* Updated wagon 2.6 to 2.8.
* Updated guava 10.0.1 to 14.0.1.


### 3.8.0 (2014-08-19)

* Moved to github. Changed coordinates to net.oneondone.maven:embedded and package accordingly.



### 3.7.0 (2014-04-01)

* Detect resolution problems when loading a pom.
* Removed explicit proxy selector - loaded from settings instead.


### 3.6.0 (2014-03-17)

* Switch from Sonatype Aether to Eclipse Aether.


### 3.5.1 (2014-01-27)

* Cleanup api


### 3.5.0 ()2014-01-27)

* Configurable user settings.
* Configurable settings file.


### 3.4.0 (2014-01-22)

* Update wagon 1.0 to 2.6, plexus utils 2.0.7 to 3.0.8, and maven 3.0.4 to 3.0.5.


### 3.3.1 (2014-01-11)

* Align packages with groupId. Removed 3.3.0 release from Artifactory.



### 3.3.0 (2014-01-11)

* Maven object is always created from settings now; creation from defaults for urls has been removed because it's problematic for the artifactory switch.
* Bumped version to 3.3.0 and changed groupId and packages to com.oneandone.sales.tools.maven (to comply with new naming scheme).
* Update sushi 2.8.8 to 2.8.12.
* Maven.withSettings did not configure authentication.



(see https://github.com/mlhartme/maven-embedded/blob/embedded-3.8.0/src/changes/changes.xml for older logs)
