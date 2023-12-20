import java.io.File

fun copyGradleWrapper(fromDir: File, toDir: File) {
    copyDirectory("gradle", fromDir, toDir)
    copyFile("gradlew", fromDir, toDir)
    copyFile("gradlew.bat", fromDir, toDir)
}

fun generateGradleProjects(settings: Settings) {
    val allCompiledModules = mutableListOf<CompiledModule>()

    var currentLevel = 1u
    var compiledModulesAtPreviousLevel: List<CompiledModule> = emptyList()
    var compiledModulesToGenerate = settings.amountOfLeafModules

    do {
        val isProducingMainModule = compiledModulesToGenerate == 1u // The last module is always the 'main' module.

        val slicedDependencies: List<List<CompiledModule>> = compiledModulesAtPreviousLevel.windowed(
            size = settings.amountOfDependenciesPerNonLeafModules.toInt(),
            step = settings.amountOfDependenciesPerNonLeafModules.toInt(),
            partialWindows = true
        )

        compiledModulesAtPreviousLevel = zeroPaddedSequence(maxNumber = compiledModulesToGenerate).map { moduleNumber ->
            generateCompiledModule(
                settings = settings,
                moduleName = moduleNumber.asNameOfModule(currentLevel),
                basePackageName = moduleNumber.asBasePackageNameOfModule(currentLevel),
                dependencies = slicedDependencies.getOrElse(moduleNumber.value.toInt() - 1) { emptyList() },
                isMain = isProducingMainModule
            ).also { allCompiledModules += it }
        }.toList()

        currentLevel += 1u

        if (isProducingMainModule) break

        val nonZeroRemainder = (compiledModulesToGenerate % settings.amountOfDependenciesPerNonLeafModules) > 0u
        compiledModulesToGenerate /= settings.amountOfDependenciesPerNonLeafModules
        if (nonZeroRemainder) compiledModulesToGenerate++
    } while (true)

    RootModule(
        path = settings.generatedProjectDir,
        jvmMaxHeapSpace = settings.jvmMaxHeapSpace,
        kotlinVersion = settings.kotlinVersion,
        allCompiledModules
    )
}

private fun generateCompiledModule(
    settings: Settings,
    moduleName: ModuleName,
    basePackageName: PackageName,
    dependencies: List<CompiledModule>,
    isMain: Boolean
): CompiledModule {
    return CompiledModule(
        path = settings.generatedProjectDir.resolve(moduleName),
        name = moduleName,
        useCompressedKlibs = settings.useCompressedKlibs,
        dependsOn = dependencies,
        isMain = isMain,
    ).also {
        generateSourceFilesForModule(
            it,
            basePackageName,
            settings.amountOfFilesPerModule,
            settings.amountOfDeclarationsPerFile,
            dependencies
        )
    }
}

private fun generateSourceFilesForModule(
    module: CompiledModule,
    basePackageName: PackageName,
    amountOfFilesPerModule: UInt,
    amountOfDeclarationsPerFile: UInt,
    dependencies: List<CompiledModule>
) {
    zeroPaddedSequence(maxNumber = amountOfFilesPerModule).forEach { fileNumber ->
        val sourceFile = SourceFile(
            path = module.jsMainDir.resolve(fileNumber.asNameOfFileInModule()),
            packageName = fileNumber.asPackageNameOfFileInModule(basePackageName)
        )
        module.files += sourceFile

        zeroPaddedSequence(maxNumber = amountOfDeclarationsPerFile).forEach { declarationNumber ->
            val functionName = "fun$declarationNumber"

            val function = TopLevelFunction(
                name = functionName,
                fullyQualifiedName = "${sourceFile.packageName}.$functionName",
                invokedFunctions = if (declarationNumber.value == 1u)
                    dependencies.flatMap { dependency -> dependency.files.getOrNull(fileNumber.value.toInt() - 1)?.declarations.orEmpty() }
                else
                    emptyList()
            )

            sourceFile.declarations += function
        }

        sourceFile.commit()
    }
}

@JvmInline private value class ModuleName(val value: String) {
    init {
        check(value.all { it == '-' || it.isLetterOrDigit() }) {
            "Invalid module name: $value"
        }
    }

    override fun toString() = value
}

@JvmInline private value class PackageName(val value: String) {
    init {
        check(value.all { it == '.' || it.isLetterOrDigit() }) {
            "Invalid package name: $value"
        }
    }

    override fun toString() = value
}

@JvmInline private value class FileName(val value: String) {
    init {
        check(value.all { it == '-' || it == '.' || it.isLetterOrDigit() } && value.endsWith(".kt")) {
            "Invalid file name: $value"
        }
    }

    override fun toString() = value
}

private class SourceFile(private val path: File, val packageName: PackageName) {
    val declarations: MutableList<TopLevelFunction> = mutableListOf()

    fun commit() {
        path.writeText(
            buildString {
                appendLine("@file:Suppress(\"PackageDirectoryMismatch\")")
                appendLine()
                appendLine("package $packageName")

                declarations.forEach { declaration ->
                    appendLine()
                    appendLine(declaration.text)
                }
            }
        )
    }
}

private class TopLevelFunction(
    name: String,
    val fullyQualifiedName: String,
    invokedFunctions: List<TopLevelFunction> = emptyList()
) {
    val text = buildString {
        appendLine("fun $name() {")
        appendLine("    println(\"$fullyQualifiedName()\")")
        invokedFunctions.forEach { invokedFunction ->
            appendLine("    ${invokedFunction.fullyQualifiedName}()")
        }
        append("}")
    }
}

private class CompiledModule(
    path: File,
    name: ModuleName,
    useCompressedKlibs: Boolean,
    dependsOn: List<CompiledModule>,
    isMain: Boolean
) {
    val gradlePath: String = ":$name"
    val jsMainDir: File = path.resolve("src/jsMain/kotlin")
    val files: MutableList<SourceFile> = mutableListOf()

    init {
        //path.mkdirs()
        jsMainDir.mkdirs()

        generateBuildGradleKts(path.resolve("build.gradle.kts"), useCompressedKlibs, dependsOn, isMain)
    }

    private fun generateBuildGradleKts(buildGradleKts: File, useCompressedKlibs: Boolean, dependsOn: List<CompiledModule>, isMain: Boolean) {
        val artifactTaskName = if (useCompressedKlibs) {
            // "jsJar" produces compressed KLIB file
            // NOTE: "jsJar" depends on "compileKotlinJs" task
            "jsJar"
        } else {
            // "compileKotlinJs" produces uncompressed KLIB
            "compileKotlinJs"
        }

        buildGradleKts.writeText(
            buildString {
                appendLine("import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile")
                if (isMain) {
                    appendLine("import java.time.Duration")
                    appendLine("import java.time.Instant")
                    appendLine("import kotlin.time.toKotlinDuration")
                }
                appendLine()
                appendLine("kotlin {")
                appendLine("    js { nodejs() }")
                appendLine("}")
                appendLine()
                appendLine("fun isKlib(file: File): Boolean {")
                appendLine("    return (file.isFile && file.extension == \"klib\") || (file.isDirectory && file.resolve(\"default/manifest\").isFile)")
                appendLine("}")
                appendLine()
                appendLine("val compileKotlinJs by tasks.getting(Kotlin2JsCompile::class) {")
                appendLine("    doFirst {")
                appendLine("        val classpath = libraries.mapNotNull { it.relativeToOrNull(rootProject.projectDir)?.path }.filter { \"..\" !in it }")
                appendLine("        println(\"CLASSPATH OF TASK ${'$'}path (${'$'}{classpath.size} entries):\")")
                appendLine("        classpath.forEach { entry -> println(\"    ${'$'}entry\") }")
                appendLine("    }")
                dependsOn.forEach { dependency ->
                    appendLine()
                    appendLine("    dependsOn(\"${dependency.gradlePath}:$artifactTaskName\")")
                    appendLine("    libraries.from(provider { project(\"${dependency.gradlePath}\").tasks.getByName(\"$artifactTaskName\").outputs.files.filter(::isKlib) })")
                }
                appendLine("}")
                if (isMain) {
                    appendLine()
                    appendLine("val measureCompileTime: Task by tasks.creating {")
                    appendLine("    dependsOn(compileKotlinJs)")
                    appendLine()
                    appendLine("    doLast {")
                    appendLine("        val startTime: Instant by gradle.extra")
                    appendLine("        val duration = Duration.between(startTime, Instant.now()).toKotlinDuration()")
                    appendLine("        println(\"Task ${'$'}path finished in ${'$'}duration\")")
                    appendLine("    }")
                    appendLine("}")
                }
            }
        )
    }
}

private class RootModule(
    path: File,
    jvmMaxHeapSpace: String,
    kotlinVersion: String,
    allCompiledModules: List<CompiledModule>
) {
    init {
        generateBuildGradleKts(path.resolve("build.gradle.kts"), kotlinVersion)
        generateSettingsGradleKts(path.resolve("settings.gradle.kts"), allCompiledModules)
        generateGradleProperties(path.resolve("gradle.properties"), jvmMaxHeapSpace)
    }

    private fun generateBuildGradleKts(buildGradleKts: File, kotlinVersion: String) {
        buildGradleKts.writeText(
            """
            import java.time.Instant

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
            
            gradle.projectsEvaluated {
                val startTime: Instant  by extra { Instant.now() }
            }
            """.trimIndent()
        )
    }

    private fun generateSettingsGradleKts(settingsGradleKts: File, allCompiledModules: List<CompiledModule>) {
        settingsGradleKts.writeText(
            buildString {
                appendLine("rootProject.name = \"generated-project-with-${allCompiledModules.size}-modules\"")
                appendLine()
                allCompiledModules.forEach { compiledModule ->
                    appendLine("include(\"${compiledModule.gradlePath}\")")
                }
            }
        )
    }

    private fun generateGradleProperties(gradleProperties: File, jvmMaxHeapSpace: String) {
        gradleProperties.writeText(
            buildString {
                appendLine("org.gradle.caching=false")
                appendLine("org.gradle.daemon=false")
                appendLine("org.gradle.console=plain")
                if (jvmMaxHeapSpace.isNotBlank()) appendLine("org.gradle.jvmargs=-Xmx$jvmMaxHeapSpace")
            }
        )
    }
}

private fun ZeroPadded<UInt>.asNameOfModule(level: UInt): ModuleName = ModuleName("module-level$level-$this")
private fun ZeroPadded<UInt>.asBasePackageNameOfModule(level: UInt): PackageName = PackageName("module.level$level.n$this")

private fun ZeroPadded<UInt>.asNameOfFileInModule(): FileName = FileName("file-$this.kt")
private fun ZeroPadded<UInt>.asPackageNameOfFileInModule(baseModulePackageName: PackageName): PackageName = PackageName("$baseModulePackageName.file$this")

private fun File.resolve(moduleName: ModuleName): File = resolve(moduleName.value)
private fun File.resolve(fileName: FileName): File = resolve(fileName.value)
