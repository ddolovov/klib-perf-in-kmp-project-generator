plugins {
    kotlin("jvm") version "1.9.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
}

kotlin {
    jvmToolchain(17)
}

val run by tasks.getting(JavaExec::class) {
    mainClass = "MainKt"
}

val generateProjects by tasks.creating(Task::class) {
    dependsOn(run)
}
