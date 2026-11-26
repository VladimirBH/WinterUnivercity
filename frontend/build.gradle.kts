val python = providers.gradleProperty("pythonExecutable").orElse(
    if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
)

tasks.register<Exec>("runBackend") {
    group = "application"
    description = "Runs the WinterUnivercity Python API on the development computer."
    workingDir = rootProject.file("../backend")
    commandLine(python.get(), "-u", "main.py")
}
