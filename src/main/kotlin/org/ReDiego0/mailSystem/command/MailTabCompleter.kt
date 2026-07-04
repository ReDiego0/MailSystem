package org.ReDiego0.mailSystem.command

import org.ReDiego0.mailSystem.config.ComplexMailLoader
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class MailTabCompleter(
    private val complexMailLoader: ComplexMailLoader
) : TabCompleter {

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("send", "sendcomplex", "purge").filter {
                it.startsWith(args[0].lowercase())
            }
            2 -> when (args[0].lowercase()) {
                "send" -> getTargetSuggestions(args[1])
                "sendcomplex" -> getComplexIdSuggestions(args[1])
                "purge" -> getPlayerSuggestions(args[1])
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private fun getTargetSuggestions(prefix: String): List<String> {
        val suggestions = mutableListOf("*", "@online")
        suggestions.addAll(getPlayerSuggestions(prefix))
        return suggestions
    }

    private fun getPlayerSuggestions(prefix: String): List<String> {
        return Bukkit.getOnlinePlayers()
            .map { it.name }
            .filter { it.lowercase().startsWith(prefix.lowercase()) }
    }

    private fun getComplexIdSuggestions(prefix: String): List<String> {
        return complexMailLoader.getAllIds()
            .filter { it.lowercase().startsWith(prefix.lowercase()) }
    }
}
