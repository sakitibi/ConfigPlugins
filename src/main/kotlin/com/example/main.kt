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
import com.mojang.brigadier.arguments.BoolArgumentType

object ConfigPlugins : ModInitializer {

    // Boolean型で扱う設定キーのセット（後から追加しやすい）
    private val booleanConfigKeys = mutableSetOf("analytics", "tcm")

    // すべての設定は変更可かどうかで管理（trueなら変更不可）
    private val configDefinitions = mutableMapOf(
        "analytics" to false,
        "tcm" to false,
        "sys" to true
    )

    // コマンド登録のときBoolean設定はBoolArgumentTypeで引数を受け取る
override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val root = CommandManager.literal("config")

            for ((key, locked) in configDefinitions) {
                if (locked) continue

                if (key in booleanConfigKeys) {
                    // Boolean型設定
                    root.then(
                        CommandManager.literal(key)
                            .then(
                                CommandManager.argument("value", BoolArgumentType.bool())
                                    .executes { context ->
                                        val value = BoolArgumentType.getBool(context, "value")
                                        val source = context.source
                                        val player = source.entity as? ServerPlayerEntity ?: run {
                                            source.sendError(Text.literal("このコマンドはプレイヤーのみ実行可能です。"))
                                            return@executes 0
                                        }
                                        val getTagsMethod = player.javaClass.getMethod("getScoreboardTags")
                                        val tags = getTagsMethod.invoke(player) as Set<String>

                                        if (!tags.contains("admin")) {
                                            source.sendError(Text.literal("このコマンドを実行するには管理者権限が必要です。"))
                                            return@executes 0
                                        }

                                        val success = setConfigValue(source.server, key, value.toString())
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
                } else {
                    // String型設定（例）
                    root.then(
                        CommandManager.literal(key)
                            .then(
                                CommandManager.argument("value", StringArgumentType.word())
                                    .executes { context ->
                                        val value = StringArgumentType.getString(context, "value")
                                        val source = context.source
                                        val player = source.entity as? ServerPlayerEntity ?: run {
                                            source.sendError(Text.literal("このコマンドはプレイヤーのみ実行可能です。"))
                                            return@executes 0
                                        }
                                        val getTagsMethod = player.javaClass.getMethod("getScoreboardTags")
                                        val tags = getTagsMethod.invoke(player) as Set<String>

                                        if (!tags.contains("admin")) {
                                            source.sendError(Text.literal("このコマンドを実行するには管理者権限が必要です。"))
                                            return@executes 0
                                        }

                                        val success = setConfigValue(source.server, key, value)
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
                }
            }

            dispatcher.register(root)
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
