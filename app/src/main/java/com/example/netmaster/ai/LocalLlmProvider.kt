package com.example.netmaster.ai

import com.example.netmaster.data.Lesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable private data class OllamaRequest(val model:String,val prompt:String,val stream:Boolean=false)
@Serializable private data class OllamaResponse(val response:String="")
@Serializable private data class ChatMessage(val role:String,val content:String)
@Serializable private data class ChatRequest(val model:String,val messages:List<ChatMessage>,val temperature:Double=0.1,val stream:Boolean=false)
@Serializable private data class ChatChoice(val message:ChatMessage)
@Serializable private data class ChatResponse(val choices:List<ChatChoice> = emptyList())

enum class LocalLlmProtocol{OLLAMA,OPENAI_COMPATIBLE}

class LocalLlmProvider(
    private val endpoint:String,
    private val model:String,
    private val protocol:LocalLlmProtocol=LocalLlmProtocol.OLLAMA,
    private val connectTimeoutMs:Int=5000,
    private val readTimeoutMs:Int=15000
):AiProvider {
    private val json=Json{ignoreUnknownKeys=true;isLenient=true}
    override suspend fun ask(mode:AiMode,prompt:String,context:List<Lesson>):AiResponse=withContext(Dispatchers.IO){
        val contextText=context.take(8).joinToString("\n\n"){lesson->"# ${lesson.title}\n${lesson.technical}\n${lesson.packetWalkthrough}\n${lesson.troubleshooting}"}.take(18000)
        val system="""You are NetMaster ${mode.name}. You are a network-engineering tutor and incident-analysis assistant.\nRules: reason from evidence; distinguish symptom, hypothesis and finding; never claim a command was executed on a real device; never recommend silent production changes; when uncertain ask for a measurable test; output practical commands with platform labels.\n""".trimIndent()
        val body=when(protocol){
            LocalLlmProtocol.OLLAMA->json.encodeToString(OllamaRequest(model,"$system\n\nKnowledge:\n$contextText\n\nUser:\n$prompt",false))
            LocalLlmProtocol.OPENAI_COMPATIBLE->json.encodeToString(ChatRequest(model,listOf(ChatMessage("system",system),ChatMessage("user","Knowledge:\n$contextText\n\nUser:\n$prompt")),0.1,false))
        }
        try{
            val path=when(protocol){LocalLlmProtocol.OLLAMA->"/api/generate";LocalLlmProtocol.OPENAI_COMPATIBLE->"/v1/chat/completions"}
            val conn=(URL(endpoint.trimEnd('/')+path).openConnection() as HttpURLConnection).apply{
                requestMethod="POST";connectTimeout=connectTimeoutMs;readTimeout=readTimeoutMs;doOutput=true
                setRequestProperty("Content-Type","application/json");setRequestProperty("Accept","application/json")
            }
            try{
                conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
                val code=conn.responseCode
                if(code !in 200..299) throw IllegalStateException("Local LLM HTTP $code")
                val raw=conn.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText()}
                val answer=when(protocol){LocalLlmProtocol.OLLAMA->json.decodeFromString<OllamaResponse>(raw).response;LocalLlmProtocol.OPENAI_COMPATIBLE->json.decodeFromString<ChatResponse>(raw).choices.firstOrNull()?.message?.content.orEmpty()}
                if(answer.isBlank())throw IllegalStateException("Local LLM returned empty response")
                AiResponse("Local LLM • $model",answer)
            }finally{conn.disconnect()}
        }catch(e:Exception){AiResponse("Local LLM unavailable","اتصال به مدل Local برقرار نشد: ${e.message}. تنظیمات Endpoint/Model/Protocol را بررسی کن و در صورت نیاز Offline AI را فعال کن.")}
    }
}
