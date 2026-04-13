package io.github.manasmods.manas_cosmetics.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.core.CosmeticManager;
import io.github.manasmods.manas_cosmetics.data.PlayerCosmeticData;
import io.github.manasmods.manas_cosmetics.network.CosmeticsNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Registers all /manas_cosmetics sub-commands.
 *
 *  /manas_cosmetics give <player> <id>   – OP only
 *  /manas_cosmetics list                 – OP only
 *  /manas_cosmetics reload               – OP only
 *  /manas_cosmetics wardrobe             – any player
 */
public final class ManasCosmteticsCommand {

    private ManasCosmteticsCommand() {}

    private static final SuggestionProvider<CommandSourceStack> COSMETIC_ID_SUGGESTIONS =
        (ctx, builder) -> {
            CosmeticManager.get().getAllDefinitions()
                .stream()
                .map(CosmeticDefinition::id)
                .filter(id -> id.startsWith(builder.getRemaining()))
                .forEach(builder::suggest);
            return builder.buildFuture();
        };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("manas_cosmetics")
                .then(literal("give")
                    .requires(src -> src.hasPermission(2))
                    .then(argument("player", EntityArgument.player())
                        .then(argument("id", StringArgumentType.string())
                            .suggests(COSMETIC_ID_SUGGESTIONS)
                            .executes(ManasCosmteticsCommand::executeGive)
                        )
                    )
                )
                .then(literal("list")
                    .requires(src -> src.hasPermission(2))
                    .executes(ManasCosmteticsCommand::executeList)
                )
                .then(literal("generate")
                    .requires(src -> src.hasPermission(2))
                    .executes(ManasCosmteticsCommand::executeGenerate)
                )
                .then(literal("reload")
                    .requires(src -> src.hasPermission(2))
                    .executes(ManasCosmteticsCommand::executeReload)
                )
                .then(literal("wardrobe")
                    .executes(ManasCosmteticsCommand::executeWardrobe)
                )
        );
    }

    // ── /manas_cosmetics give <player> <id> ────────────────────────────────────

    private static int executeGive(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String id = StringArgumentType.getString(ctx, "id");

        if (!CosmeticManager.get().exists(id)) {
            ctx.getSource().sendFailure(Component.literal("Unknown cosmetic id: " + id));
            return 0;
        }

        CosmeticDefinition def = CosmeticManager.get().getDefinition(id).orElseThrow();
        PlayerCosmeticData data = PlayerCosmeticData.of(target);
        data.equip(def.slot(), id);

        CosmeticsNetworking.syncToTrackers(target);

        ctx.getSource().sendSuccess(
            () -> Component.literal("Gave cosmetic '" + def.displayName() + "' to " + target.getName().getString()),
            true
        );
        return 1;
    }

    // ── /manas_cosmetics list ──────────────────────────────────────────────────

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        Collection<CosmeticDefinition> defs = CosmeticManager.get().getAllDefinitions();

        if (defs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No cosmetics loaded."), false);
            return 0;
        }

        ctx.getSource().sendSuccess(
            () -> Component.literal("Loaded cosmetics (" + defs.size() + "):"),
            false
        );
        for (CosmeticDefinition def : defs) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("  " + def.id() + " [" + def.slot().getId() + "] – " + def.displayName()),
                false
            );
        }
        return defs.size();
    }

    // ── /manas_cosmetics generate ─────────────────────────────────────────────

    private static int executeGenerate(CommandContext<CommandSourceStack> ctx) {
        int generated = CosmeticManager.get().generateSidecars();

        // Always reload so any freshly written sidecars are live immediately.
        CosmeticManager.get().reload();
        int loaded = CosmeticManager.get().getAllDefinitions().size();

        if (ctx.getSource().getServer() != null) {
            io.github.manasmods.manas_cosmetics.ManasCosmetics.broadcastRegistryToAll(ctx.getSource().getServer());
        }

        final int finalGenerated = generated;
        final int finalLoaded    = loaded;
        ctx.getSource().sendSuccess(
            () -> Component.literal(
                "[manas_cosmetics] Generated " + finalGenerated + " sidecar(s). "
                + finalLoaded + " cosmetic(s) now loaded."
            ),
            true
        );
        return generated;
    }

    // ── /manas_cosmetics reload ────────────────────────────────────────────────

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CosmeticManager.get().reload();
        int count = CosmeticManager.get().getAllDefinitions().size();

        // Push updated registry to all connected clients
        if (ctx.getSource().getServer() != null) {
            io.github.manasmods.manas_cosmetics.ManasCosmetics.broadcastRegistryToAll(ctx.getSource().getServer());
        }

        ctx.getSource().sendSuccess(
            () -> Component.literal("[manas_cosmetics] Reloaded. " + count + " cosmetic(s) loaded."),
            true
        );
        return count;
    }

    // ── /manas_cosmetics wardrobe ──────────────────────────────────────────────

    private static int executeWardrobe(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        // Send a sync packet so the client has fresh data, then signal it to open the GUI.
        CosmeticsNetworking.sendSyncToPlayer(
            io.github.manasmods.manas_cosmetics.network.SyncPlayerCosmeticsPayload.of(player),
            player
        );

        dev.architectury.networking.NetworkManager.sendToPlayer(
            player,
            new io.github.manasmods.manas_cosmetics.network.WardrobePayloads.OpenWardrobeS2CPayload()
        );

        return 1;
    }
}
