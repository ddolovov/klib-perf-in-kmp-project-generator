import java.io.File
import java.nio.file.Files

interface SettingsOrBuilder {
    /** User home dir at the current workstation. */
    val userHome: File

    /** Current working dir. By default, the root of the 'project-generator' project. */
    val workDir: File

    /** Temp dir. */
    val tempDir: File

    /** The directory where the generated KMP project will be saved. By default, the same as [tempDir]. */
    val generatedProjectDir: File

    /** The version of Kotlin to be used in the generated KMP project. */
    val kotlinVersion: KotlinVersion

    /** `true` - use compressed (archive files) KLIBs, `false` - use uncompressed KLIBs (directories). */
    val useCompressedKlibs: Boolean

    /** The amount of libraries in the generated KMP project. */
    val amountOfLibraries: UInt
}

interface SettingsBuilder : SettingsOrBuilder {
    override var userHome: File
    override var workDir: File
    override var tempDir: File
    override var generatedProjectDir: File
    override var kotlinVersion: KotlinVersion
    override var useCompressedKlibs: Boolean
    override val amountOfLibraries: UInt
}

interface Settings : SettingsOrBuilder {
    fun dump(): String
}

fun Settings(build: SettingsBuilder.() -> Unit): Settings {
    val builder = SettingsBuilderImpl()
    builder.build()
    return SettingsImpl(builder)
}

private class SettingsBuilderImpl : SettingsBuilder {
    override var userHome: File = getSystemDir("user.home")
    override var workDir: File = getSystemDir("user.dir")
    override var tempDir: File = Files.createTempDirectory("project-generator").toFile()
    override var generatedProjectDir: File = tempDir
    override var kotlinVersion: KotlinVersion = KotlinVersion.CURRENT
    override var useCompressedKlibs: Boolean = false
    override var amountOfLibraries: UInt = 2u
}

private class SettingsImpl(
    override val userHome: File,
    override val workDir: File,
    override val tempDir: File,
    override val generatedProjectDir: File,
    override val kotlinVersion: KotlinVersion,
    override val useCompressedKlibs: Boolean,
    override val amountOfLibraries: UInt,
) : Settings {
    constructor(other: SettingsOrBuilder) : this(
        userHome = other.userHome,
        workDir = other.workDir,
        tempDir = other.tempDir,
        generatedProjectDir = other.generatedProjectDir,
        kotlinVersion = other.kotlinVersion,
        useCompressedKlibs = other.useCompressedKlibs,
        amountOfLibraries = other.amountOfLibraries,
    )

    override fun dump(): String = buildString {
        appendLine("Settings {")
        SettingsImpl::class.java.declaredFields.forEach { field ->
            append("\t").append(field.name).append(" = ").appendLine(field.get(this@SettingsImpl))
        }
        appendLine("}")
    }
}

private fun getSystemDir(name: String): File = System.getProperty(name)?.let(::File) ?: error("Can't obtain '$name'")
