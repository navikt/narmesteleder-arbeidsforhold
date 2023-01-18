# narmesteleder-arbeidsforhold
This project contains the application code and infrastructure for narmesteleder-arbeidsforhold

## Technologies used
* Kotlin
* Ktor
* Gradle

#### Requirements

* JDK 17
* Docker

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the GitHub Package Registry which requires authentication. 
We have 3 ways this can be solved:

1. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.
See githubs guide [creating-a-personal-access-token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) on
how to create a personal access token.

2. The variables can be configured as environment variables like this:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

3. The variables can be configured in command line arguments:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

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