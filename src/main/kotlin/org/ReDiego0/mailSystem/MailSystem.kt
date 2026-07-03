package org.ReDiego0.mailSystem

import org.ReDiego0.mailSystem.api.MailApi
import org.ReDiego0.mailSystem.command.MailCommand
import org.ReDiego0.mailSystem.condition.ConditionEvaluator
import org.ReDiego0.mailSystem.config.ComplexMailLoader
import org.ReDiego0.mailSystem.config.MessageManager
import org.ReDiego0.mailSystem.config.SimpleMailTemplate
import org.ReDiego0.mailSystem.gui.MailGui
import org.ReDiego0.mailSystem.gui.MailGuiListener
import org.ReDiego0.mailSystem.maintenance.ExpirationTask
import org.ReDiego0.mailSystem.manager.MailManager
import org.ReDiego0.mailSystem.manager.SimpleMailManager
import org.ReDiego0.mailSystem.storage.MailStorage
import org.ReDiego0.mailSystem.storage.SqlMailStorage
import org.ReDiego0.mailSystem.storage.StorageConfig
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.Executors

class MailSystem : JavaPlugin() {

    private lateinit var executor: java.util.concurrent.ExecutorService
    private lateinit var storage: MailStorage
    private lateinit var manager: MailManager
    private lateinit var gui: MailGui
    private lateinit var messageManager: MessageManager

    override fun onEnable() {
        generateConfigIfMissing()

        messageManager = MessageManager(this)
        messageManager.load()

        val storageConfig = StorageConfig.fromConfig(config)
        val inboxCapacity = config.getInt("maintenance.inbox_capacity", 50)
        val expirationHours = config.getLong("maintenance.expiration_interval_hours", 6)

        executor = Executors.newFixedThreadPool(4)
        storage = SqlMailStorage(storageConfig, executor, this)
        manager = SimpleMailManager(storage, executor, this, inboxCapacity)

        storage.initialize().thenRun {
            logger.info("MailSystem storage ready")
        }.exceptionally { e ->
            logger.severe("Failed to initialize storage: ${e.message}")
            null
        }

        server.servicesManager.register(MailApi::class.java, manager.getApi(), this, ServicePriority.Normal)

        gui = MailGui(this, manager, messageManager)
        server.pluginManager.registerEvents(MailGuiListener(gui, messageManager), this)

        val template = SimpleMailTemplate.fromConfig(config)
        val complexMailLoader = ComplexMailLoader(this)
        complexMailLoader.load()
        val conditionEvaluator = ConditionEvaluator(this)
        getCommand("mail")?.setExecutor(MailCommand(this, manager, gui, template, complexMailLoader, conditionEvaluator, messageManager))

        ExpirationTask(this, manager, expirationHours).start()

        instance = this
        _manager = manager
        _gui = gui
        _messageManager = messageManager

        logger.info("MailSystem enabled")
    }

    override fun onDisable() {
        if (::storage.isInitialized) storage.shutdown().join()
        if (::executor.isInitialized) executor.shutdown()
        logger.info("MailSystem disabled")
    }

    private fun generateConfigIfMissing() {
        val configFile = File(dataFolder, "config.yml")
        if (configFile.exists()) return

        dataFolder.mkdirs()
        val yaml = YamlConfiguration()
        yaml.set("simple_mail.sender_name", "Server")
        yaml.set("simple_mail.sender_icon", "default")
        yaml.set("simple_mail.ttl_days", 15)
        yaml.set("storage.type", "sqlite")
        yaml.set("storage.sqlite.file", "data.db")
        yaml.set("storage.mysql.host", "localhost")
        yaml.set("storage.mysql.port", 3306)
        yaml.set("storage.mysql.database", "mail_system")
        yaml.set("storage.mysql.username", "root")
        yaml.set("storage.mysql.password", "")
        yaml.set("storage.mysql.pool_size", 10)
        yaml.set("maintenance.expiration_interval_hours", 6)
        yaml.set("maintenance.inbox_capacity", 50)
        yaml.save(configFile)
        logger.info("Generated default config.yml")
    }

    companion object {
        @JvmStatic
        lateinit var instance: MailSystem
            private set

        private lateinit var _manager: MailManager
        private lateinit var _gui: MailGui
        private lateinit var _messageManager: MessageManager

        @JvmStatic
        fun getManager(): MailManager = _manager

        @JvmStatic
        fun getApi(): MailApi = _manager.getApi()

        @JvmStatic
        fun getGui(): MailGui = _gui

        @JvmStatic
        fun getMessageManager(): MessageManager = _messageManager
    }
}
