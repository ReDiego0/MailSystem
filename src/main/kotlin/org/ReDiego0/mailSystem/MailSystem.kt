package org.ReDiego0.mailSystem

import org.ReDiego0.mailSystem.api.MailApi
import org.ReDiego0.mailSystem.command.MailCommand
import org.ReDiego0.mailSystem.condition.ConditionEvaluator
import org.ReDiego0.mailSystem.config.ComplexMailLoader
import org.ReDiego0.mailSystem.config.SimpleMailTemplate
import org.ReDiego0.mailSystem.gui.MailGui
import org.ReDiego0.mailSystem.gui.MailGuiListener
import org.ReDiego0.mailSystem.manager.MailManager
import org.ReDiego0.mailSystem.manager.SimpleMailManager
import org.ReDiego0.mailSystem.storage.MailStorage
import org.ReDiego0.mailSystem.storage.SqlMailStorage
import org.ReDiego0.mailSystem.storage.StorageConfig
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Executors

class MailSystem : JavaPlugin() {

    private lateinit var executor: java.util.concurrent.ExecutorService
    private lateinit var storage: MailStorage
    private lateinit var manager: MailManager
    private lateinit var gui: MailGui

    override fun onEnable() {
        saveDefaultConfig()

        val storageConfig = StorageConfig.fromConfig(config)
        executor = Executors.newFixedThreadPool(4)
        storage = SqlMailStorage(storageConfig, executor, this)
        manager = SimpleMailManager(storage, executor, this)

        storage.initialize().thenRun {
            logger.info("MailSystem storage ready")
        }.exceptionally { e ->
            logger.severe("Failed to initialize storage: ${e.message}")
            null
        }

        server.servicesManager.register(MailApi::class.java, manager.getApi(), this, ServicePriority.Normal)

        gui = MailGui(this, manager)
        server.pluginManager.registerEvents(MailGuiListener(gui), this)

        val template = SimpleMailTemplate.fromConfig(config)
        val complexMailLoader = ComplexMailLoader(this)
        complexMailLoader.load()
        val conditionEvaluator = ConditionEvaluator(this)
        getCommand("mail")?.setExecutor(MailCommand(this, manager, gui, template, complexMailLoader, conditionEvaluator))

        instance = this
        _manager = manager
        _gui = gui

        logger.info("MailSystem enabled")
    }

    override fun onDisable() {
        if (::storage.isInitialized) storage.shutdown().join()
        if (::executor.isInitialized) executor.shutdown()
        logger.info("MailSystem disabled")
    }

    companion object {
        @JvmStatic
        lateinit var instance: MailSystem
            private set

        private lateinit var _manager: MailManager
        private lateinit var _gui: MailGui

        @JvmStatic
        fun getManager(): MailManager = _manager

        @JvmStatic
        fun getApi(): MailApi = _manager.getApi()

        @JvmStatic
        fun getGui(): MailGui = _gui
    }
}
