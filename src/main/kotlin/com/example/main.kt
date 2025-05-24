package com.example

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtString
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateManager
import java.util.function.Supplier
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.entity.Entity

object ConfigPlugins : ModInitializer {

    // 設定引数の定義。trueなら変更不可
    private val configDefinitions = mapOf(
        "analytics" to false,
        "settings" to false,
        "tcm" to false,
        "sys" to true       // 変更不可
    )

    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("config")
                    .then(
                        CommandManager.argument("key", StringArgumentType.word())
                            .then(
                                CommandManager.argument("value", StringArgumentType.word())
                                    .executes { context ->
                                        val key = StringArgumentType.getString(context, "key")
                                        val value = StringArgumentType.getString(context, "value")
                                        val source = context.source
                                        val server = source.server

                                        val player = source.entity as? ServerPlayerEntity
                                        if (player == null) {
                                            source.sendError(Text.literal("このコマンドはプレイヤーのみ実行可能です。"))
                                            return@executes 0
                                        }

                                        val playerName = player.name.string
                                        val tags = player.javaClass.getMethod("getScoreboardTags").invoke(player) as Set<String>

                                        if (!tags.contains("admin")) {
                                            source.sendError(Text.literal("このコマンドを実行するには管理者権限が必要です。"))
                                            return@executes 0
                                        }

                                        if (!configDefinitions.containsKey(key)) {
                                            source.sendError(Text.literal("設定「$key」は存在しません。"))
                                            return@executes 0
                                        }

                                        if (configDefinitions[key] == true) {
                                            source.sendError(Text.literal("設定「$key」は変更不可です。\nhelp 設定使用した項目は、もしかしたら\nゲーム内設定項目にある可能性があります\nありましたらそちらで設定を変更して下さい"))
                                            return@executes 0
                                        }

                                        val success = setConfigValue(server, key, value)

                                        if (success) {
                                            source.sendFeedback(Text.literal("設定「$key」を「$value」に変更しました。"), false)
                                            1
                                        } else {
                                            source.sendError(Text.literal("設定「$key」の値「$value」が無効です。"))
                                            0
                                        }
                                    }
                            )
                    )
            )
        }
    }

    private fun setConfigValue(server: MinecraftServer, key: String, value: String): Boolean {
        val overworld = server.overworld
        val stateManager = overworld.persistentStateManager

        val configState = stateManager.getOrCreate(
            { nbt -> ConfigPersistentState(nbt) },
            { ConfigPersistentState() },
            "configplugins"
        )

        return configState.setValue(key, value)
    }

    // PersistentStateを使ってNBTに保存する例
    class ConfigPersistentState() : PersistentState() {

        private val data = mutableMapOf<String, String>()

        constructor(nbt: NbtCompound) : this() {
            for (key in nbt.keys) {
                data[key] = nbt.getString(key)
            }
        }

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            for ((key, value) in data) {
                nbt.putString(key, value)
            }
            return nbt
        }

        fun setValue(key: String, value: String): Boolean {
            data[key] = value
            markDirty()
            return true
        }
    }
}