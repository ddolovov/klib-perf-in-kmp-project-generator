fun main() {
    val settings = Settings {
        // TODO: adjust the settings here as necessary
        generatedProjectDir = userHome.resolve("temp/generated-project")
        useCompressedKlibs = true
    }
    println(settings.dump())

    makeEmptyDirectory(settings.generatedProjectDir)
    copyGradleWrapper(fromDir = settings.workDir, toDir = settings.generatedProjectDir)

    generateGradleProjects(
        settings.amountOfLibraries,
        settings.kotlinVersion,
        rootProjectDir = settings.generatedProjectDir,
        useCompressedKlibs = settings.useCompressedKlibs
    )
}
