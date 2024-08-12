# narmesteleder-arbeidsforhold
This project contains the application code and infrastructure for narmesteleder-arbeidsforhold

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kotest

#### Requirements

* JDK 21
* Docker

## Getting started
### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run 

``` bash
./gradlew shadowJar
``` 
or on Windows
``` bash
gradlew.bat shadowJar
``` 

#### Creating a docker image
Creating a docker image should be as simple as 
``` bash 
docker build -t narmesteleder-arbeidsforhold .
```

#### Running a docker image
``` bash 
docker run --rm -it -p 8080:8080 narmesteleder-arbeidsforhold
```

### Upgrading the gradle wrapper

Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

``` bash 
./gradlew wrapper --gradle-version $gradleVersjon
```

### Contact

This project is maintained by [teamsykmelding](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/narmesteleder-arbeidsforhold/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)