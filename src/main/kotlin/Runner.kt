import java.io.File

fun copyRunnerFile(fromDir: File, toDir: File) {
    copyFile("run-measurements-serie.py", fromDir.resolve("src/main/python"), toDir)
}
