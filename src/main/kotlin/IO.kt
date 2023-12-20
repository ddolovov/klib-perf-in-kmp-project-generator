import java.io.File
import java.nio.file.Files

fun makeEmptyDirectory(directory: File) {
    directory.deleteRecursively()
    directory.mkdirs()
}

fun copyDirectory(name: String, fromDir: File, toDir: File) {
    val directory = fromDir.resolve(name)
    check(directory.isDirectory) { "Directory $directory does not exist" }
    directory.copyRecursively(toDir.resolve(name))
}

fun copyFile(name: String, fromDir: File, toDir: File) {
    val file = fromDir.resolve(name)
    check(file.isFile) { "File $file does not exist" }

    // Files.copy() to preserve permissions!
    Files.copy(file.toPath(), toDir.resolve(name).toPath())
}
