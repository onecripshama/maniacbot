import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.TelegramFile
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
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

fun loadVoiceCommands(): List<VoiceCommand> {
    val classLoader = Thread.currentThread().contextClassLoader
    val descriptionsStream = classLoader.getResourceAsStream("voices/descriptions.txt")
        ?: error("Файл descriptions.txt не найден в resources/voices")

    val descriptionMap = descriptionsStream.bufferedReader().readLines()
        .mapNotNull { line ->
            val parts = line.split(" - ", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
        .toMap()

    val commands = mutableListOf<VoiceCommand>()

    for ((indexStr, description) in descriptionMap) {
        val oggFilename = "$indexStr.ogg"
        val oggPath = "voices/$oggFilename"
        val commandName = "voice$indexStr"
        // Проверяем, существует ли файл
        if (classLoader.getResource(oggPath) != null) {
            commands.add(VoiceCommand(commandName, oggPath, description))
        } else {
            println("⚠️ Пропущен: $oggFilename не найден в ресурсах.")
        }
    }

    return commands.sortedBy { it.commandName }
}
