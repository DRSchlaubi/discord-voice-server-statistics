package dev.schlaubi.discordvoicestats

import dev.schlaubi.envconf.Config

object Config : Config() {
    val IP_INFO_TOKEN by this
    val TOKEN by this
    val API_SERVER by this
}