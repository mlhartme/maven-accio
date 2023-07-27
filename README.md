# maven-embedded

This is a library to resolve artifacts and load poms files. It's kind of a stripped-down Maven for only this functionality 
behind a simple api to be consumed by other Java code. It does not do any build, it does not run any plugins, and it does 
not include a launcher shell script.

This library does not load any extensions code, neither core extensions, nor plugin extensions, nor build extensions.
When loading a pom, it is considered secure with regard to code injection.

