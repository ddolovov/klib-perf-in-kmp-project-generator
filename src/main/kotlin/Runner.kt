import java.io.File

fun copyRunnerFile(fromDir: File, toDir: File) {
    copyFile("make-measurements.py", fromDir.resolve("src/main/python"), toDir)
}
