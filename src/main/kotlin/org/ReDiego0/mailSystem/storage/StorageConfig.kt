package org.ReDiego0.mailSystem.storage

import org.bukkit.configuration.Configuration

data class StorageConfig(
    val type: String,
    val sqliteFile: String,
    val mysqlHost: String,
    val mysqlPort: Int,
    val mysqlDatabase: String,
    val mysqlUsername: String,
    val mysqlPassword: String,
    val mysqlPoolSize: Int
) {
    val isSqlite: Boolean get() = type == "sqlite"
    val isMysql: Boolean get() = type == "mysql"

    companion object {
        fun fromConfig(config: Configuration): StorageConfig = StorageConfig(
            type = config.getString("storage.type", "sqlite")!!,
            sqliteFile = config.getString("storage.sqlite.file", "data.db")!!,
            mysqlHost = config.getString("storage.mysql.host", "localhost")!!,
            mysqlPort = config.getInt("storage.mysql.port", 3306),
            mysqlDatabase = config.getString("storage.mysql.database", "mail_system")!!,
            mysqlUsername = config.getString("storage.mysql.username", "root")!!,
            mysqlPassword = config.getString("storage.mysql.password", "")!!,
            mysqlPoolSize = config.getInt("storage.mysql.pool_size", 10)
        )
    }
}
