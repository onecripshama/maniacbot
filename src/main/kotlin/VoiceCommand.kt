import java.io.File

data class VoiceCommand(val commandName: String, val oggPath: String, val description: String) {
    fun toTempFile(): File {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(oggPath)
            ?: error("Не найден ресурс $oggPath")
        val temp = File.createTempFile(commandName, ".ogg")
        stream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        return temp
    }
}