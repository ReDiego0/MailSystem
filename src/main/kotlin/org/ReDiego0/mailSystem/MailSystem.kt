package org.ReDiego0.mailSystem

import org.ReDiego0.mailSystem.api.MailApi
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

        instance = this
        _manager = manager

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

        @JvmStatic
        fun getManager(): MailManager = _manager

        @JvmStatic
        fun getApi(): MailApi = _manager.getApi()
    }
}
