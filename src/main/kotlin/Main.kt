import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.jar.JarFile
import kotlin.concurrent.thread

fun main() {
    val token = System.getenv("TELEGRAM_BOT_TOKEN") ?: error("ERROR: TELEGRAM_BOT_TOKEN not set")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    // HTTP-сервер для Render
    thread {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext("/") { exchange ->
            val response = "Bot is running"
            if (exchange.requestMethod == "HEAD") {
                exchange.sendResponseHeaders(200, -1)
            } else {
                exchange.sendResponseHeaders(200, response.length.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }
            }
        }
        server.executor = null
        server.start()
        println("Fake HTTP server started on port $port")
    }

    val voiceCommands = loadVoiceCommands()

    val bot = bot {
        this.token = token

        dispatch {
            command("start") {
                val chatId = ChatId.fromId(message.chat.id)
                val list = voiceCommands.joinToString("\n") { "/${it.commandName} — ${it.description}" }
                bot.sendMessage(chatId, "Привет! Вот список голосовых команд:\n$list")
            }

            message {
                val text = message.text ?: return@message
                val chatId = ChatId.fromId(message.chat.id)

                val matched = voiceCommands.find { "/${it.commandName}" == text }
                if (matched != null) {
                    val voiceFile = matched.toTempFile()
                    bot.sendVoice(chatId = chatId, audio = TelegramFile.ByFile(voiceFile))
                    voiceFile.delete()
                }
            }
        }
    }

    bot.startPolling()
}

data class VoiceCommand(val commandName: String, val oggPath: String, val description: String) {
    fun toTempFile(): File {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(oggPath)
            ?: error("Не найден ресурс $oggPath")
        val temp = File.createTempFile(commandName, ".ogg")
        stream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        return temp
    }
}

fun loadVoiceCommands(): List<VoiceCommand> {
    val classLoader = Thread.currentThread().contextClassLoader
    val voicesUrl = classLoader.getResource("voices") ?: return emptyList()

    val oggFiles = mutableListOf<String>()

    if (voicesUrl.protocol == "jar") {
        val jarPath = voicesUrl.path.substringAfter("file:").substringBefore("!/")
        JarFile(jarPath).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("voices/") && entry.name.endsWith(".ogg")) {
                    oggFiles.add(entry.name)
                }
            }
        }
    } else {
        val dir = File(voicesUrl.toURI())
        dir.listFiles { f -> f.extension == "ogg" }?.forEach {
            oggFiles.add("voices/${it.name}")
        }
    }

    oggFiles.sort()

    val descriptionsStream = classLoader.getResourceAsStream("voices/descriptions.txt")
        ?: error("Файл descriptions.txt не найден в voices/")
    val descriptions = descriptionsStream.bufferedReader().readLines()

    if (descriptions.size < oggFiles.size) {
        error("Недостаточно описаний: ${descriptions.size} описаний на ${oggFiles.size} файлов")
    }

    return oggFiles.mapIndexed { index, oggPath ->
        VoiceCommand("voice${index + 1}", oggPath, descriptions[index])
    }
}
