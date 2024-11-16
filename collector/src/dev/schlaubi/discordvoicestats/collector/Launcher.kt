package dev.schlaubi.discordvoicestats.collector

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.SQLException

const val currentMigration = 2L

suspend fun main() {
    val database = connectDatabase()

    Server(database).init().start(wait = true)
}

private suspend fun connectDatabase(): Database {
    val dataSource = HikariConfig().apply {
        jdbcUrl = Config.DATABASE_URL
        schema = "voice_servers"
    }

    val driver = HikariDataSource(dataSource).asJdbcDriver()
    val database = Database(driver)

    blocking {
        try {
            val migration = database.functionsQueries.getLatestAppliedMigration().executeAsOne()
            if (migration.version < currentMigration) {
                Database.Schema.migrate(driver, migration.version + 1, currentMigration + 1).await()
                database.functionsQueries.insertAppliedMigration(currentMigration)
            }
        } catch (e: SQLException) {
            Database.Schema.create(driver).await()
            database.functionsQueries.insertAppliedMigration(currentMigration)
        }
    }
    return database
}
