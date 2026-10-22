package com.hermes.mobile

import com.hermes.mobile.transport.dto.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProtocolSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }


    @Test
    fun testClientHelloSerialization() {
        val hello = ClientHelloDto(deviceId = "test_dev_01")
        val serialized = json.encodeToString(ClientHelloDto.serializer(), hello)

        assertTrue(serialized.contains("\"type\":\"client_hello\""))
        assertTrue(serialized.contains("\"device_id\":\"test_dev_01\""))
        assertTrue(serialized.contains("\"protocol_version\":\"1.0\""))
    }

    @Test
    fun testServerReadyDeserialization() {
        val rawJson = """
            {
                "type": "server_ready",
                "status": "ready",
                "server_version": "0.9.4",
                "session_id": "sess_12345",
                "active_model": "Hermes 3 (Llama-3.1 8B)",
                "capabilities": ["streaming", "tool_approval", "clarify"]
            }
        """.trimIndent()

        val ready = json.decodeFromString<ServerReadyDto>(rawJson)
        assertEquals("server_ready", ready.type)
        assertEquals("ready", ready.status)
        assertEquals("0.9.4", ready.serverVersion)
        assertEquals("sess_12345", ready.sessionId)
        assertEquals(3, ready.capabilities.size)
    }

    @Test
    fun testStreamChunkDeserialization() {
        val rawJson = """
            {
                "type": "stream_chunk",
                "message_id": "msg_001",
                "session_id": "sess_001",
                "delta": "Привет, мир!",
                "sequence": 1
            }
        """.trimIndent()

        val chunk = json.decodeFromString<StreamChunkDto>(rawJson)
        assertEquals("stream_chunk", chunk.type)
        assertEquals("msg_001", chunk.messageId)
        assertEquals("Привет, мир!", chunk.delta)
        assertEquals(1, chunk.sequence)
    }

    @Test
    fun testToolApprovalSerialization() {
        val response = ToolApprovalResponseDto(
            requestId = "req_99",
            sessionId = "sess_01",
            decision = "approve"
        )
        val serialized = json.encodeToString(ToolApprovalResponseDto.serializer(), response)
        assertTrue(serialized.contains("\"decision\":\"approve\""))
    }

    @Test
    fun testClarifyDeserialization() {
        val rawJson = """
            {
                "type": "clarify_prompt",
                "prompt_id": "clr_42",
                "session_id": "sess_01",
                "question": "Какой формат выбрать?",
                "options": ["JSON", "Markdown", "Text"],
                "is_multi_choice": false
            }
        """.trimIndent()

        val prompt = json.decodeFromString<ClarifyPromptDto>(rawJson)
        assertEquals("clr_42", prompt.promptId)
        assertEquals(3, prompt.options.size)
        assertEquals("JSON", prompt.options[0])
    }

    @Test
    fun testMessageCompleteDeserializationWithThinking() {
        val rawJson = """
            {
                "type": "message_complete",
                "message_id": "msg_complete_01",
                "session_id": "sess_01",
                "final_content": "Результат вычислений: 42",
                "thinking": "Шаг 1: Получаем задачу. Шаг 2: Вычисляем 6 * 7 = 42."
            }
        """.trimIndent()

        val complete = json.decodeFromString<MessageCompleteDto>(rawJson)
        assertEquals("message_complete", complete.type)
        assertEquals("Результат вычислений: 42", complete.finalContent)
        assertEquals("Шаг 1: Получаем задачу. Шаг 2: Вычисляем 6 * 7 = 42.", complete.thinking)
    }

    @Test
    fun testThinkingExtractionRegex() {
        val raw = "<think>\nРазмышляю над ответом...\nПроверяю факты...\n</think>\nВот итоговый ответ."
        val thinkRegex = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.IGNORE_CASE)
        val match = thinkRegex.find(raw)
        assertNotNull(match)
        val thinking = match!!.groupValues[1].trim()
        val cleanContent = raw.replace(match.value, "").trim()

        assertEquals("Размышляю над ответом...\nПроверяю факты...", thinking)
        assertEquals("Вот итоговый ответ.", cleanContent)
    }

    @Test
    fun testCustomEndpointSerialization() {
        val dto = com.hermes.mobile.transport.dto.CustomEndpointDto(
            name = "DeepSeek",
            apiMode = "chat_completions",
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "sk-test123456",
            models = listOf("deepseek-chat", "deepseek-coder")
        )

        val encoded = json.encodeToString(com.hermes.mobile.transport.dto.CustomEndpointDto.serializer(), dto)
        val decoded = json.decodeFromString<com.hermes.mobile.transport.dto.CustomEndpointDto>(encoded)

        assertEquals("DeepSeek", decoded.name)
        assertEquals("chat_completions", decoded.apiMode)
        assertEquals("https://api.deepseek.com/v1", decoded.baseUrl)
        assertEquals("sk-test123456", decoded.apiKey)
        assertEquals(2, decoded.models.size)
        assertEquals("deepseek-chat", decoded.models[0])
    }

    @Test
    fun testProviderApiModeMapping() {
        assertEquals(com.hermes.mobile.domain.model.ProviderApiMode.OPENAI_COMPATIBLE, com.hermes.mobile.domain.model.ProviderApiMode.fromId("chat_completions"))
        assertEquals(com.hermes.mobile.domain.model.ProviderApiMode.ANTHROPIC_MESSAGES, com.hermes.mobile.domain.model.ProviderApiMode.fromId("anthropic_messages"))
        assertEquals(com.hermes.mobile.domain.model.ProviderApiMode.OLLAMA_NATIVE, com.hermes.mobile.domain.model.ProviderApiMode.fromId("ollama"))
        assertEquals(com.hermes.mobile.domain.model.ProviderApiMode.GOOGLE_GEMINI, com.hermes.mobile.domain.model.ProviderApiMode.fromId("gemini"))
        // fallback to default
        assertEquals(com.hermes.mobile.domain.model.ProviderApiMode.OPENAI_COMPATIBLE, com.hermes.mobile.domain.model.ProviderApiMode.fromId("unknown_mode"))
    }

    @Test
    fun testProviderNameLengthConstraint() {
        val longName = "VeryLongProviderNameExceeding16Chars"
        val trimmedName = longName.trim().take(16)
        assertEquals(16, trimmedName.length)
        assertEquals("VeryLongProvider", trimmedName)
    }

    @Test
    fun testSlashCommandItemSerialization() {
        val customCmd = com.hermes.mobile.domain.model.SlashCommandItem(
            command = "/quota",
            title = "Проверка квот",
            description = "Запрос лимита токенов и баланса провайдеров",
            example = "/quota",
            actionId = "custom",
            isCustom = true
        )
        val encoded = json.encodeToString(com.hermes.mobile.domain.model.SlashCommandItem.serializer(), customCmd)
        val decoded = json.decodeFromString<com.hermes.mobile.domain.model.SlashCommandItem>(encoded)

        assertEquals("/quota", decoded.command)
        assertEquals("Проверка квот", decoded.title)
        assertEquals(true, decoded.isCustom)
        assertEquals("custom", decoded.actionId)
    }
}
