fun main() = generateProjects(
    // TODO: adjust the settings here as necessary
    {
        useCompressedKlibs = true
        amountOfLeafModules = 100u

        kotlinVersion = "2.0.0-Beta2"
        generatedProjectDir = userHome.resolve("temp").resolve(inventProjectName())
    },
    {
        useCompressedKlibs = false
        amountOfLeafModules = 100u

        kotlinVersion = "2.0.0-Beta2"
        generatedProjectDir = userHome.resolve("temp").resolve(inventProjectName())
    },
    {
        useCompressedKlibs = true
        amountOfLeafModules = 400u

        kotlinVersion = "2.0.0-Beta2"
        generatedProjectDir = userHome.resolve("temp").resolve(inventProjectName())
    },
    {
        useCompressedKlibs = false
        amountOfLeafModules = 400u

        kotlinVersion = "2.0.0-Beta2"
        generatedProjectDir = userHome.resolve("temp").resolve(inventProjectName())
    },
    {
        useCompressedKlibs = true
        amountOfLeafModules = 1000u

        kotlinVersion = "2.0.0-Beta2"
        generatedProjectDir = userHome.resolve("temp").resolve(inventProjectName())
    },
    {
        useCompressedKlibs = false
        amountOfLeafModules = 1000u

        kotlinVersion = "2.0.0-Beta2"
        generatedProjectDir = userHome.resolve("temp").resolve(inventProjectName())
    },
)

private fun generateProjects(vararg configs: SettingsBuilder.() -> Unit) {
    println("KMP projects to be generated: ${configs.size}")

    configs.forEachIndexed { index, config ->
        println()
        println("========================================")
        println("Project ${index + 1}")

        val settings = Settings(config)
        println(settings.dump())

        makeEmptyDirectory(settings.generatedProjectDir)

        copyGradleWrapper(fromDir = settings.workDir, toDir = settings.generatedProjectDir)
        generateGradleProjects(settings)

        copyRunnerFile(fromDir = settings.workDir, toDir = settings.generatedProjectDir)

        println("Done")
    }
}

private fun SettingsBuilder.inventProjectName(): String =
    "generated-kmp-project-$amountOfLeafModules-klib-${if (useCompressedKlibs) "archives" else "dirs"}"
