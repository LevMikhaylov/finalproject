package com.example

import com.example.routing.authRoutes
import com.example.routing.carRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.concurrent.ConcurrentHashMap
import java.time.Duration
val websocketSessions = ConcurrentHashMap<String, WebSocketSession>()
fun Application.configureRouting() {
    install(io.ktor.server.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        authRoutes()
        carRoutes()
        webSocket("/ws") {
            val sessionId = call.parameters["id"] ?: "anonymous_${System.currentTimeMillis()}"
            websocketSessions[sessionId] = this

            try {
                send("Вы подключились к WebSocket. Ваш ID: $sessionId")
                broadcast("Пользователь $sessionId подключился")

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val receivedText = frame.readText()
                            // Отправляем сообщение всем подключенным клиентам
                            broadcast("Пользователь $sessionId: $receivedText")
                        }
                        else -> { }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                println("WebSocket канал закрыт для $sessionId")
            } catch (e: Throwable) {
                println("Ошибка WebSocket: ${e.message}")
            } finally {
                websocketSessions.remove(sessionId)
                broadcast("Пользователь $sessionId отключился")
            }
        }
        // Корневой маршрут
        get("/") {
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "User Management API",
                    "endpoints" to listOf(
                        "GET /users - Get all users",
                        "GET /users/{id} - Get user by ID",
                        "POST /users - Create new user",
                        "DELETE /users/{id} - Delete user"
                    )
                )
            )
        }

        // GET /users/{id} - получить пользователя по ID
        get("/users/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid user ID")

            val user = users.find { it.id == id }
                ?: throw NotFoundException("User with ID $id not found")

            call.respond(HttpStatusCode.OK, user)
        }

        // POST /users - создать нового пользователя
        post("/users") {
            val user = call.receive<User>()

            // Валидация
            if (user.username.isBlank() || user.password.isBlank()) {
                throw IllegalArgumentException("Username and password are required")
            }

            if (users.any { it.username == user.username }) {
                throw ConflictException("User with this username already exists")
            }

            // Генерация ID
            val newId = (users.maxOfOrNull { it.id } ?: 0) + 1
            val newUser = user.copy(id = newId)
            users.add(newUser)

            call.respond(HttpStatusCode.Created, newUser)
        }

        // DELETE /users/{id} - удалить пользователя
        delete("/users/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid user ID")

            val user = users.find { it.id == id }
                ?: throw NotFoundException("User with ID $id not found")

            users.remove(user)
            call.respond(HttpStatusCode.OK, mapOf("message" to "User deleted successfully"))
        }


    }
}
suspend fun broadcast(message: String) {
    val sessionsToRemove = mutableListOf<String>()

    websocketSessions.forEach { (sessionId, session) ->
        try {
            session.send(message)
        } catch (e: Exception) {
            println("Ошибка отправки сообщения для $sessionId: ${e.message}")
            sessionsToRemove.add(sessionId)
        }
    }

    // Удаляем нерабочие сессии
    sessionsToRemove.forEach { websocketSessions.remove(it) }
}


class NotFoundException(message: String) : Exception(message)
class ConflictException(message: String) : Exception(message)