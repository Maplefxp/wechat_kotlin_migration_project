package com.sbnkj.assistant.core.util

object WechatXmlUtils {
    // private val titleRegex = Regex("<title><!\[CDATA\[(.*?)]]></title>|<title>(.*?)</title>", RegexOption.IGNORE_CASE)
    // private val lengthRegex = Regex("<length>(\d+)</length>", RegexOption.IGNORE_CASE)
    private val titleRegex = Regex("""<title><!\[CDATA\[(.*?)]]></title>|<title>(.*?)</title>""", RegexOption.IGNORE_CASE)
    private val lengthRegex = Regex("""<length>(\d+)</length>""", RegexOption.IGNORE_CASE)

    fun extractAttachmentTitle(content: String?): String? {
        if (content.isNullOrBlank()) return null
        val match = titleRegex.find(content) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim()
    }

    fun extractLengthFromReserved(reserved: String?): Long? {
        if (reserved.isNullOrBlank()) return null
        val xml = when {
            reserved.startsWith("<?xml") -> reserved
            ':' in reserved -> reserved.substringAfter(':', reserved)
            else -> reserved
        }
        val match = lengthRegex.find(xml) ?: return null
        return match.groupValues.getOrNull(1)?.toLongOrNull()
    }
}
