package cn.kurt6.cobblemon_ranked.config

import blue.endless.jankson.Comment

data class DatabaseConfig(
    @Comment("Database type: 'sqlite' or 'mysql' or 'mariadb'")
    var databaseType: String = "sqlite",

    @Comment("SQLite database file path (relative to config folder) / SQLite 数据库文件路径")
    var sqliteFile: String = "ranked.db",

    @Comment("MySQL configuration / MySQL 配置")
    var mysql: MySQLConfig = MySQLConfig()
)

data class MySQLConfig(
    @Comment("MySQL/MariaDB host address / MySQL 主机地址")
    var host: String = "localhost",

    @Comment("MySQL/MariaDB port / MySQL 端口")
    var port: Int = 3306,

    @Comment("MySQL/MariaDB database name / MySQL 数据库名")
    var database: String = "cobblemon_ranked",

    @Comment("MySQL/MariaDB username / MySQL 用户名")
    var username: String = "root",

    @Comment("MySQL/MariaDB password / MySQL 密码")
    var password: String = "",

    @Comment("MySQL/MariaDB connection pool size / MySQL 连接池大小")
    var poolSize: Int = 10,

    @Comment("MySQL/MariaDB connection timeout (ms) / MySQL 连接超时时间（毫秒）")
    var connectionTimeout: Long = 5000,

    @Comment("Additional MySQL/MariaDB connection parameters / MySQL 额外连接参数")
    var parameters: String = "useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
)