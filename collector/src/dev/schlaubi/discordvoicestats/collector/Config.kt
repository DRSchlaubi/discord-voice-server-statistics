package dev.schlaubi.discordvoicestats.collector

import dev.schlaubi.envconf.Config

object Config : Config() {
    val DATABASE_URL by this
    val TOKEN by this
}