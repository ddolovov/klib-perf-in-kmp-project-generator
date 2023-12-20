import java.io.File

fun copyGradleWrapper(fromDir: File, toDir: File) {
    copyDirectory("gradle", fromDir, toDir)
    copyFile("gradlew", fromDir, toDir)
    copyFile("gradlew.bat", fromDir, toDir)
}

fun generateGradleProjects(
    amountOfLibraries: UInt,
    kotlinVersion: KotlinVersion,
    useCompressedKlibs: Boolean,
    rootProjectDir: File
) {
    val libraryModules = generateLibraryNamesAndPackages(amountOfLibraries)
        .map { (libraryName, packageName) ->
            LibraryModule(
                path = rootProjectDir.resolve(libraryName),
                name = libraryName,
                packageName = packageName,
            )
        }

    libraryModules.forEach { it.generate() }

    val applicationModule = ApplicationModule(
        path = rootProjectDir.resolve("app"),
        name = "app",
        packageName = "app",
        libraryModules,
        useCompressedKlibs
    )
    applicationModule.generate()

    val rootModule = RootModule(
        path = rootProjectDir,
        kotlinVersion, libraryModules, applicationModule
    )
    rootModule.generate()
}

private fun generateLibraryNamesAndPackages(amountOfLibraries: UInt): List<Pair<String, String>> {
    val paddingLength = amountOfLibraries.toString().length
    return (1 ..amountOfLibraries.toInt()).map { index ->
        val suffix = index.toString().padStart(paddingLength, '0')
        val libraryName = "library-$suffix"
        val packageName = "library_$suffix"
        libraryName to packageName
    }
}

private abstract class Module(
    val path: File,
    name: String,
    val packageName: String,
) {
    val gradlePath: String = ":$name"
    val buildGradleKts: File = path.resolve("build.gradle.kts")
    val jsMainDir: File = path.resolve("src/jsMain/kotlin")

    fun generate() {
        check(!path.exists()) { "Gradle project already exists: $path" }
        path.mkdirs()

        generateBuildGradleKts()
        generateSources()
    }

    private fun generateBuildGradleKts() {
        check(!buildGradleKts.exists()) { "Gradle build file already exists: $buildGradleKts" }
        doGenerateBuildGradleKts()
    }
    protected abstract fun doGenerateBuildGradleKts()

    private fun generateSources() {
        check(!jsMainDir.exists()) { "Source directory already exists: $jsMainDir" }
        jsMainDir.mkdirs()

        doGenerateSources()
    }
    protected abstract fun doGenerateSources()
}

private class LibraryModule(
    path: File,
    name: String,
    packageName: String,
) : Module(path, name, packageName) {
    override fun doGenerateBuildGradleKts() {
        buildGradleKts.writeText(
            """
            kotlin {
                js { nodejs() }
            }
            """.trimIndent()
        )
    }

    override fun doGenerateSources() {
        jsMainDir.resolve("library.kt").writeText(
            buildString { generateStandardDeclarations(packageName) }
        )
    }
}

private class ApplicationModule(
    path: File,
    name: String,
    packageName: String,
    val libraryModules: List<LibraryModule>,
    useCompressedKlibs: Boolean
) : Module(path, name, packageName) {
    val artifactTaskName = if (useCompressedKlibs) {
        // "jsJar" produces compressed KLIB file
        // NOTE: "jsJar" depends on "compileKotlinJs" task
        "jsJar"
    } else {
        // "compileKotlinJs" produces uncompressed KLIB
        "compileKotlinJs"
    }

    override fun doGenerateBuildGradleKts() {
        buildGradleKts.writeText(
            buildString {
                appendLine(
                    """
                    import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
                    import java.time.Duration
                    import java.time.Instant
                    import kotlin.time.toKotlinDuration

                    kotlin {
                        js {
                            nodejs()
                            binaries.executable()
                        }
                    }

                    fun isKlib(file: File): Boolean {
                        return (file.isFile && file.extension == "klib") || (file.isDirectory && file.resolve("default/manifest").isFile)
                    }

                    val compileKotlinJs by tasks.getting(Kotlin2JsCompile::class) {
                        var startTime: Instant by extra

                        doFirst {
                            val classpath = libraries.toList()
                            println("CLASSPATH (${'$'}{classpath.size} entries):")
                            classpath.forEach { entry -> println("    ${'$'}entry") }
                            
                            startTime = Instant.now()
                        }
                        
                        doLast {
                            val duration = Duration.between(startTime, Instant.now()).toKotlinDuration()
                            println("Task ${'$'}path finished in ${'$'}duration")
                        }
                    """.trimIndent()
                )
                libraryModules.forEach { libraryModule ->
                    appendLine()
                    appendLine("    dependsOn(\"${libraryModule.gradlePath}:$artifactTaskName\")")
                    appendLine("    libraries.from(provider { project(\"${libraryModule.gradlePath}\").tasks.getByName(\"$artifactTaskName\").outputs.files.filter(::isKlib) })")
                }
                appendLine(
                    """
                    }
                    
                    val measureCompileTime: Task by tasks.creating {
                        dependsOn(compileKotlinJs)
                    }
                    """.trimIndent()
                )
            }
        )
    }

    override fun doGenerateSources() {
        jsMainDir.resolve("app.kt").writeText(
            buildString {
                generateStandardDeclarations(packageName)
                appendLine()
                appendLine("fun main() {")
                generateStandardCallSites(packageName)
                libraryModules.forEach { libraryModule ->
                    generateStandardCallSites(libraryModule.packageName)
                }
                appendLine("}")
            }
        )
    }
}

private class RootModule(
    val path: File,
    val kotlinVersion: KotlinVersion,
    val libraryModules: List<LibraryModule>,
    val applicationModule: ApplicationModule
) {
    val buildGradleKts: File = path.resolve("build.gradle.kts")
    val settingsGradleKts: File = path.resolve("settings.gradle.kts")
    val gradleProperties: File = path.resolve("gradle.properties")

    fun generate() {
        check(path.exists()) { "Gradle project does not exist: $path" }

        generateBuildGradleKts()
        generateSettingsGradleKts()
        generateGradleProperties()
    }

    private fun generateBuildGradleKts() {
        check(!buildGradleKts.exists()) { "Gradle build file already exists: $buildGradleKts" }
        buildGradleKts.writeText(
            """
            plugins {
                kotlin("multiplatform") version "$kotlinVersion" apply false
            }

            allprojects {
                repositories {
                    mavenCentral()
                }
                
                // Force all tasks to be never UP-TO-DATE.
                tasks.all { outputs.upToDateWhen { false } }
            }

            subprojects {
                apply(plugin = "org.jetbrains.kotlin.multiplatform")
            }
            """.trimIndent()
        )
    }

    private fun generateSettingsGradleKts() {
        check(!settingsGradleKts.exists()) { "Gradle settings file already exists: $settingsGradleKts" }
        settingsGradleKts.writeText(
            buildString {
                appendLine("rootProject.name = \"generated-project-with-${libraryModules.size}-libraries\"")
                appendLine()
                libraryModules.forEach { libraryModule ->
                    appendLine("include(\"${libraryModule.gradlePath}\")")
                }
                appendLine("include(\"${applicationModule.gradlePath}\")")
            }
        )
    }

    private fun generateGradleProperties() {
        check(!gradleProperties.exists()) { "Gradle properties file already exists: $gradleProperties" }
        gradleProperties.writeText(
            buildString {
                appendLine("org.gradle.caching=false")
                appendLine("org.gradle.daemon=false")
                appendLine("org.gradle.console=plain")
            }
        )
    }
}

private fun StringBuilder.generateStandardDeclarations(packageName: String) {
    appendLine(
        """
        package $packageName
        
        fun foo(): String = "$packageName.foo()"
                
        class Foo {
            fun foo(): String = "$packageName.Foo.foo()"
        }
        """.trimIndent()
    )
}

private fun StringBuilder.generateStandardCallSites(packageName: String) {
    appendLine(
        """|
           |    println("$packageName.foo() = " + $packageName.foo())
           |    println("$packageName.Foo.foo() = " + $packageName.Foo().foo())
        """.trimMargin()
    )
}
