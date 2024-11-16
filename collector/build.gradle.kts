plugins {
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("Database") {
            packageName = "dev.schlaubi.discordvoicestats.collector"
            dialect(libs.sqldelight.postgresql.dialect)

            migrationOutputDirectory = layout.buildDirectory.dir("resources/main/migrations")
            migrationOutputFileFormat = ".sql"
            deriveSchemaFromMigrations = true
            srcDirs(layout.projectDirectory.dir("migrations"))
        }
    }
}

tasks {
    compileKotlinJvm.configure {
        dependsOn("generateCommonMainDatabaseMigrations")
    }
}
