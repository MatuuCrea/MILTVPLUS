package com.matuucrea.miltvplus.data

import java.net.URI
import java.security.MessageDigest

object M3uParser {
    private val attr = Regex("""([\\w-]+)="([^"]*)"""")
    fun parse(text:String):List<Channel> {
        require(text.lineSequence().firstOrNull()?.trim()?.startsWith("#EXTM3U",true)==true) { "El archivo no comienza con #EXTM3U" }
        val out=mutableListOf<Channel>()
        var name=""; var group=""; var logo:String?=null
        text.lineSequence().forEach { raw ->
            val line=raw.trim()
            when {
                line.startsWith("#EXTINF",true) -> {
                    val a=attr.findAll(line.substringAfter("#EXTINF")).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name=line.substringAfterLast(',').trim().ifBlank { a["tvg-name"].orEmpty() }.ifBlank { "Canal sin nombre" }
                    group=a["group-title"].orEmpty()
                    logo=a["tvg-logo"]?.takeIf { it.startsWith("https://") }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    val valid=runCatching { URI(line).scheme?.lowercase() in setOf("http","https") }.getOrDefault(false)
                    if(valid && name.isNotBlank()) {
                        val id=MessageDigest.getInstance("SHA-256").digest("$name|$line".toByteArray()).take(12).joinToString("") { "%02x".format(it) }
                        out+=Channel(id,name,line,group,logo)
                    }
                    name=""; group=""; logo=null
                }
            }
        }
        return out.distinctBy { it.id }
    }
}
