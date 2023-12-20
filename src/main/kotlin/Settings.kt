import java.io.File
import java.lang.reflect.Modifier
import java.nio.file.Files
import kotlin.reflect.KProperty0

interface SettingsOrBuilder {
    /** User home dir at the current workstation. */
    val userHome: File

    /** Current working dir. By default, the root of the 'project-generator' project. */
    val workDir: File

    /** Temp dir. */
    val tempDir: File

    /** The value to be passed to -Xmx. */
    val jvmMaxHeapSpace: String

    /** The directory where the generated KMP project will be saved. By default, the same as [tempDir]. */
    val generatedProjectDir: File

    /** The version of Kotlin to be used in the generated KMP project. */
    val kotlinVersion: String

    /** `true` - use compressed (archive files) KLIBs, `false` - use uncompressed KLIBs (directories). */
    val useCompressedKlibs: Boolean

    /** The amount of leaf modules in the generated KMP project. */
    val amountOfLeafModules: UInt

    /** The amount of files per Gradle module in the generated KMP project. */
    val amountOfFilesPerModule: UInt

    /** The amount of declarations per a single file in the generated KMP project. */
    val amountOfDeclarationsPerFile: UInt

    /** The amount of dependencies per non-leaf module. Note that for some modules it can be lesser. */
    val amountOfDependenciesPerNonLeafModules: UInt
}

interface SettingsBuilder : SettingsOrBuilder {
    override var userHome: File
    override var workDir: File
    override var tempDir: File
    override var jvmMaxHeapSpace: String
    override var generatedProjectDir: File
    override var kotlinVersion: String
    override var useCompressedKlibs: Boolean
    override var amountOfLeafModules: UInt
    override var amountOfFilesPerModule: UInt
    override var amountOfDeclarationsPerFile: UInt
    override var amountOfDependenciesPerNonLeafModules: UInt
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
    override var jvmMaxHeapSpace: String = "1g"
    override var generatedProjectDir: File = tempDir
    override var kotlinVersion: String = KotlinVersion.CURRENT.toString()
    override var useCompressedKlibs: Boolean = false
    override var amountOfLeafModules: UInt = 100u
    override var amountOfFilesPerModule: UInt = 100u
    override var amountOfDeclarationsPerFile: UInt = 10u
    override var amountOfDependenciesPerNonLeafModules: UInt = 10u
}

private class SettingsImpl(
    override val userHome: File,
    override val workDir: File,
    override val tempDir: File,
    override val jvmMaxHeapSpace: String,
    override val generatedProjectDir: File,
    override val kotlinVersion: String,
    override val useCompressedKlibs: Boolean,
    override val amountOfLeafModules: UInt,
    override val amountOfFilesPerModule: UInt,
    override val amountOfDeclarationsPerFile: UInt,
    override val amountOfDependenciesPerNonLeafModules: UInt,
) : Settings {
    constructor(other: SettingsOrBuilder) : this(
        userHome = other.userHome,
        workDir = other.workDir,
        tempDir = other.tempDir,
        jvmMaxHeapSpace = other.jvmMaxHeapSpace,
        generatedProjectDir = other.generatedProjectDir,
        kotlinVersion = other.kotlinVersion,
        useCompressedKlibs = other.useCompressedKlibs,
        amountOfLeafModules = other.amountOfLeafModules,
        amountOfFilesPerModule = other.amountOfFilesPerModule,
        amountOfDeclarationsPerFile = other.amountOfDeclarationsPerFile,
        amountOfDependenciesPerNonLeafModules = other.amountOfDependenciesPerNonLeafModules,
    )

    init {
        sanityCheck()
    }

    override fun dump(): String = buildString {
        appendLine("Settings {")
        SettingsImpl::class.java.declaredFields.forEach { field ->
            if (field.modifiers and Modifier.STATIC == 0)
                append("\t").append(field.name).append(" = ").appendLine(field.get(this@SettingsImpl))
        }
        append("}")
    }

    companion object {
        // Just some constants to make sure valid numbers are provided:
        private const val MIN_LEAF_MODULES = 1u
        private const val MAX_LEAF_MODULES = 2000u

        private const val MIN_FILES_PER_MODULE = 1u
        private const val MAX_FILES_PER_MODULE = 2000u

        private const val MIN_DECLARATIONS_PER_FILE = 0u
        private const val MAX_DECLARATIONS_PER_FILE = 1000u

        private const val MIN_DEPENDENCIES_PER_NON_LEAF_MODULE = 2u
        private const val MAX_DEPENDENCIES_PER_NON_LEAF_MODULE = 20u

        private fun SettingsImpl.sanityCheck() {
            sanityCheck(::amountOfLeafModules, MIN_LEAF_MODULES, MAX_LEAF_MODULES)
            sanityCheck(::amountOfFilesPerModule, MIN_FILES_PER_MODULE, MAX_FILES_PER_MODULE)
            sanityCheck(::amountOfDeclarationsPerFile, MIN_DECLARATIONS_PER_FILE, MAX_DECLARATIONS_PER_FILE)
            sanityCheck(::amountOfDependenciesPerNonLeafModules, MIN_DEPENDENCIES_PER_NON_LEAF_MODULE, MAX_DEPENDENCIES_PER_NON_LEAF_MODULE)
        }

        private fun sanityCheck(property: KProperty0<UInt>, minValue: UInt, maxValue: UInt) {
            check(property.get() in minValue..maxValue) {
                "${property.name} must be between $minValue and $maxValue"
            }
        }
    }
}

private fun getSystemDir(name: String): File = System.getProperty(name)?.let(::File) ?: error("Can't obtain '$name'")
