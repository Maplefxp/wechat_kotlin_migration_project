package com.sbnkj.assistant.core.model

enum class WechatUserSlot(val userId: Int, val dirName: String) {
    MAIN(0, "0"),
    CLONE(999, "999");

    val isClone: Boolean get() = this == CLONE

    companion object {
        fun fromUserId(userId: Int): WechatUserSlot = if (userId == 999) CLONE else MAIN
    }
}
