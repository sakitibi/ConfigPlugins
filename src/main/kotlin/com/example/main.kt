// Copyright 2025 SKNewRoles
package com.example

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateManager
import net.minecraft.server.network.ServerPlayerEntity

class ConfigPlugins : ModInitializer {

    enum class SettingType { BOOLEAN, STRING, NUMBER }

    data class NumberConstraint(val min: Int, val max: Int)
    data class NumberConstraintFloat(val min: Float, val max: Float)
    data class ConfigDefinition(
        val type: SettingType,
        val locked: Boolean = false,
        val numberConstraint: NumberConstraint? = null
    )

    private val configDefinitions = mutableMapOf(
        "analytics" to ConfigDefinition(SettingType.BOOLEAN),
        "tcm" to ConfigDefinition(SettingType.BOOLEAN),
        "settings" to ConfigDefinition(SettingType.STRING),
        // 例 "data" to ConfigDefinition(SettingType.NUMBER, numberConstraint = NumberConstraint(min, max)),
        "sys" to ConfigDefinition(SettingType.STRING, locked = true),
        "anattribute" to ConfigDefinition(SettingType.BOOLEAN),
        "anim" to ConfigDefinition(SettingType.NUMBER, numberConstraint = NumberConstraint(0, 1)),
        "inventory_lock" to ConfigDefinition(SettingType.NUMBER, numberConstraint = NumberConstraint(0, 1)),
        "judge_mode" to ConfigDefinition(SettingType.NUMBER, numberConstraint = NumberConstraint(0, 1)),
        "13ninAdManager" to ConfigDefinition(SettingType.NUMBER, numberConstraint = NumberConstraint(0, 1)),
        "seer_madness" to ConfigDefinition(SettingType.BOOLEAN),
        "login" to ConfigDefinition(SettingType.BOOLEAN, locked = true),
        "comuner" to ConfigDefinition(SettingType.NUMBER, numberConstraint = NumberConstraint(0, 3))
    )

    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val root = CommandManager.literal("config")

            for ((key, def) in configDefinitions) {
                if (def.locked) continue

                val command = when (def.type) {
                    SettingType.BOOLEAN -> CommandManager.literal(key)
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                            .executes { ctx ->
                                val value = BoolArgumentType.getBool(ctx, "value")
                                applySetting(ctx.source, key, value.toString())
                            })

                    SettingType.STRING -> CommandManager.literal(key)
                        .then(CommandManager.argument("value", StringArgumentType.word())
                            .executes { ctx ->
                                val value = StringArgumentType.getString(ctx, "value")
                                applySetting(ctx.source, key, value)
                            })

                    SettingType.NUMBER -> CommandManager.literal(key)
                        .then(CommandManager.argument("value", StringArgumentType.word())
                            .executes { ctx ->
                                val valueStr = StringArgumentType.getString(ctx, "value")
                                val valueInt = valueStr.toIntOrNull()
                                if (valueInt == null) {
                                    ctx.source.sendError(Text.literal("数値を入力してください"))
                                    return@executes 0
                                }
                                val constraint = def.numberConstraint
                                if (constraint != null &&
                                    (valueInt < constraint.min || valueInt > constraint.max)) {
                                    ctx.source.sendError(Text.literal("値は ${constraint.min} ～ ${constraint.max} にしてください"))
                                    return@executes 0
                                }
                                applySetting(ctx.source, key, valueInt.toString())
                            })
                }

                root.then(command)
            }

            dispatcher.register(root)
        }
    }

    private fun applySetting(source: ServerCommandSource, key: String, value: String): Int {
        val player = source.entity as? ServerPlayerEntity ?: run {
            source.sendError(Text.literal("このコマンドはプレイヤーのみ実行可能です。"))
            return 0
        }

        val tags = player.commandTags

        if (!tags.contains("admin")) {
            source.sendError(Text.literal("このコマンドを実行するには管理者権限が必要です。"))
            return 0
        }

        val success = setConfigValue(source.server, key, value)
        if (!success) {
            source.sendError(Text.literal("ワールドがまだ初期化されていません。"))
            return 0
        }
        return if (success) {
            source.sendFeedback(Text.literal("設定{$key}を「$value」に変更しました。"), false)
            1
        } else {
            source.sendError(Text.literal("Error 設定{$key}の値{$value}が無効です。"))
            0
        }
    }

    private fun setConfigValue(server: MinecraftServer, key: String, value: String): Boolean {
        val overworld = server.getWorld(net.minecraft.world.World.OVERWORLD)
            ?: return false

        val stateManager = overworld.persistentStateManager

        val configState = stateManager.getOrCreate(
            { nbt -> ConfigPersistentState(nbt) },
            { ConfigPersistentState() },
            "configplugins"
        )

        return configState.setValue(key, value)
    }

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
