# The generator of Gradle KMP projects for KLIB IO performance tests

**How to generate test projects:**
1. Edit [src/main/kotlin/main.kt](src/main/kotlin/main.kt) to setup the desired configuration. Please, pay attention to the destination (the `generatedProjectDir` property) where new project will be generated.
2. Run `./gradlew :generateProjects`

**How to run test projects:**
1. Go to the directory with the generated project.
2. Run `./make-measurements.py`. It will run 20 rounds of measurements, then print the mean compile time and the standard deviation.
